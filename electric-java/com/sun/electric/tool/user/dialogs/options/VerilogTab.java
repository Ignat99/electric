/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: VerilogTab.java
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

import com.sun.electric.database.text.Setting;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;

import javax.swing.JPanel;

/**
 * Class to handle the "Verilog" tab of the Project Preferences dialog.
 */
public class VerilogTab extends PreferencePanel
{
    private Setting verilogUseAssignSetting = SimulationTool.getVerilogUseAssignSetting();
    private Setting verilogUseTriregSetting = SimulationTool.getVerilogUseTriregSetting();
    
	/** Creates new form VerilogTab */
	public VerilogTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();
	}

	/** return the JPanel to use for the user preferences. */
	public JPanel getUserPreferencesPanel() { return preferences; }

	/** return the JPanel to use for the project Preferences. */
	public JPanel getProjectPreferencesPanel() { return projectSettings; }

	/** return the name of this preferences tab. */
	public String getName() { return "Verilog"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Verilog tab.
	 */
	public void init()
	{
		// user preferences
		stopAtStandardCells.setSelected(SimulationTool.getVerilogStopAtStandardCells());
        netlistNonstandardCells.setSelected(SimulationTool.getVerilogNetlistNonstandardCells());
        preserveVerilogFormatting.setSelected(SimulationTool.getPreserveVerilogFormating());
		parameterizeModuleNames.setSelected(SimulationTool.getVerilogParameterizeModuleNames());
        runPlacement.setSelected(SimulationTool.getVerilogRunPlacementTool());
        makeLayoutCells.setSelected(IOTool.isVerilogMakeLayoutCells());
        writeModuleForEachIcon.setSelected(SimulationTool.isVerilogWriteModuleForEachIcon());
        noEmptyModules.setSelected(SimulationTool.isVerilogNoWriteEmptyModules());

        // project preferences
		verUseAssign.setSelected(getBoolean(verilogUseAssignSetting));
		verDefWireTrireg.setSelected(getBoolean(verilogUseTriregSetting));
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Verilog tab.
	 */
	public void term()
	{
		// user preferences
		SimulationTool.setVerilogStopAtStandardCells(stopAtStandardCells.isSelected());
        SimulationTool.setVerilogNetlistNonstandardCells(netlistNonstandardCells.isSelected());
        SimulationTool.setPreserveVerilogFormating(preserveVerilogFormatting.isSelected());
		SimulationTool.setVerilogParameterizeModuleNames(parameterizeModuleNames.isSelected());
		SimulationTool.setVerilogRunPlacementTool(runPlacement.isSelected());
		IOTool.setVerilogMakeLayoutCells(makeLayoutCells.isSelected());
		SimulationTool.setVerilogWriteModuleForEachIcon(writeModuleForEachIcon.isSelected());
		SimulationTool.setVerilogNoWriteEmptyModules(noEmptyModules.isSelected());

		// project preferences
        setBoolean(verilogUseAssignSetting, verUseAssign.isSelected());
        setBoolean(verilogUseTriregSetting, verDefWireTrireg.isSelected());
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		// user preferences
		if (SimulationTool.getFactoryVerilogStopAtStandardCells() != SimulationTool.getVerilogStopAtStandardCells())
			SimulationTool.setVerilogStopAtStandardCells(SimulationTool.getFactoryVerilogStopAtStandardCells());
        if (SimulationTool.getFactoryVerilogNetlistNonstandardCells() != SimulationTool.getVerilogNetlistNonstandardCells())
            SimulationTool.setVerilogNetlistNonstandardCells(SimulationTool.getFactoryVerilogNetlistNonstandardCells());
        if (SimulationTool.getFactoryPreserveVerilogFormating() != SimulationTool.getPreserveVerilogFormating())
			SimulationTool.setPreserveVerilogFormating(SimulationTool.getFactoryPreserveVerilogFormating());
		if (SimulationTool.getFactoryVerilogParameterizeModuleNames() != SimulationTool.getVerilogParameterizeModuleNames())
			SimulationTool.setVerilogParameterizeModuleNames(SimulationTool.getFactoryVerilogParameterizeModuleNames());
		if (SimulationTool.getFactoryVerilogRunPlacementTool() != SimulationTool.getVerilogRunPlacementTool())
			SimulationTool.setVerilogRunPlacementTool(SimulationTool.getFactoryVerilogRunPlacementTool());
		if (IOTool.isFactoryVerilogMakeLayoutCells() != IOTool.isVerilogMakeLayoutCells())
			IOTool.setVerilogMakeLayoutCells(IOTool.isFactoryVerilogMakeLayoutCells());
		if (SimulationTool.isFactoryVerilogWriteModuleForEachIcon() != SimulationTool.isVerilogWriteModuleForEachIcon())
			SimulationTool.setVerilogWriteModuleForEachIcon(SimulationTool.isFactoryVerilogWriteModuleForEachIcon());
		if (SimulationTool.isFactoryVerilogNoWriteEmptyModules() != SimulationTool.isVerilogNoWriteEmptyModules())
            SimulationTool.setVerilogNoWriteEmptyModules(SimulationTool.isFactoryVerilogNoWriteEmptyModules());

		// project preferences
        setBoolean(verilogUseAssignSetting, ((Boolean)verilogUseAssignSetting.getFactoryValue()).booleanValue());
        setBoolean(verilogUseTriregSetting, ((Boolean)verilogUseTriregSetting.getFactoryValue()).booleanValue());
    }

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jSeparator1 = new javax.swing.JSeparator();
        preferences = new javax.swing.JPanel();
        inputPanel = new javax.swing.JPanel();
        runPlacement = new javax.swing.JCheckBox();
        makeLayoutCells = new javax.swing.JCheckBox();
        outputPanel = new javax.swing.JPanel();
        stopAtStandardCells = new javax.swing.JCheckBox();
        preserveVerilogFormatting = new javax.swing.JCheckBox();
        parameterizeModuleNames = new javax.swing.JCheckBox();
        writeModuleForEachIcon = new javax.swing.JCheckBox();
        netlistNonstandardCells = new javax.swing.JCheckBox();
        noEmptyModules = new javax.swing.JCheckBox();
        projectSettings = new javax.swing.JPanel();
        verDefWireTrireg = new javax.swing.JCheckBox();
        verUseAssign = new javax.swing.JCheckBox();

        setTitle("Tool Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(jSeparator1, gridBagConstraints);

        preferences.setLayout(new java.awt.GridBagLayout());

        inputPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Input"));
        inputPanel.setLayout(new java.awt.GridBagLayout());

        runPlacement.setText("Run Placement after import");
        runPlacement.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        inputPanel.add(runPlacement, gridBagConstraints);

        makeLayoutCells.setText("Make Layout Cells (not Schematics)");
        makeLayoutCells.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        inputPanel.add(makeLayoutCells, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        preferences.add(inputPanel, gridBagConstraints);

        outputPanel.setBorder(javax.swing.BorderFactory.createTitledBorder("Output"));
        outputPanel.setLayout(new java.awt.GridBagLayout());

        stopAtStandardCells.setText("Do not netlist Standard Cells");
        stopAtStandardCells.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(stopAtStandardCells, gridBagConstraints);

        preserveVerilogFormatting.setText("Preserve Verilog formatting");
        preserveVerilogFormatting.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(preserveVerilogFormatting, gridBagConstraints);

        parameterizeModuleNames.setText("Parameterize Verilog module names");
        parameterizeModuleNames.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(parameterizeModuleNames, gridBagConstraints);

        writeModuleForEachIcon.setText("Write Separate Module for each Icon");
        writeModuleForEachIcon.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(writeModuleForEachIcon, gridBagConstraints);

        netlistNonstandardCells.setText("Netlist Non-Standard Cells");
        netlistNonstandardCells.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(netlistNonstandardCells, gridBagConstraints);

        noEmptyModules.setText("Do not include empty modules");
        noEmptyModules.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        outputPanel.add(noEmptyModules, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        preferences.add(outputPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        getContentPane().add(preferences, gridBagConstraints);

        projectSettings.setLayout(new java.awt.GridBagLayout());

        verDefWireTrireg.setText("Default wire is Trireg");
        verDefWireTrireg.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        projectSettings.add(verDefWireTrireg, gridBagConstraints);

        verUseAssign.setText("Use ASSIGN Construct");
        verUseAssign.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        projectSettings.add(verUseAssign, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
        getContentPane().add(projectSettings, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel inputPanel;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JCheckBox makeLayoutCells;
    private javax.swing.JCheckBox netlistNonstandardCells;
    private javax.swing.JCheckBox noEmptyModules;
    private javax.swing.JPanel outputPanel;
    private javax.swing.JCheckBox parameterizeModuleNames;
    private javax.swing.JPanel preferences;
    private javax.swing.JCheckBox preserveVerilogFormatting;
    private javax.swing.JPanel projectSettings;
    private javax.swing.JCheckBox runPlacement;
    private javax.swing.JCheckBox stopAtStandardCells;
    private javax.swing.JCheckBox verDefWireTrireg;
    private javax.swing.JCheckBox verUseAssign;
    private javax.swing.JCheckBox writeModuleForEachIcon;
    // End of variables declaration//GEN-END:variables

}
