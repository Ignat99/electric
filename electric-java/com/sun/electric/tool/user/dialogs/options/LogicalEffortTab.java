/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LogicalEffortTab.java
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
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.logicaleffort.LETool;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.util.TextUtils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;

import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Class to handle the "Logical Effort" tab of the Project Preferences dialog.
 */
public class LogicalEffortTab extends PreferencePanel
{
	/** Creates new form LogicalEffortTab */
	public LogicalEffortTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(leGlobalFanOut);
	    EDialog.makeTextFieldSelectAllOnTab(leConvergence);
	    EDialog.makeTextFieldSelectAllOnTab(leMaxIterations);
	    EDialog.makeTextFieldSelectAllOnTab(leKeeperSizeRatio);
	    EDialog.makeTextFieldSelectAllOnTab(leGateCapacitance);
	    EDialog.makeTextFieldSelectAllOnTab(leDefaultWireCapRatio);
	    EDialog.makeTextFieldSelectAllOnTab(leDiffToGateCapRatio);
	}

	/** return the JPanel to use for the user preferences. */
	public JPanel getUserPreferencesPanel() { return null; }

	/** return the JPanel to use for the project preferences. */
	public JPanel getProjectPreferencesPanel() { return logicalEffort; }

	/** return the name of this preferences tab. */
	public String getName() { return "Logical Effort"; }

	private boolean changingLE;
    
    private Setting useLocalSettingsSetting = LETool.getUseLocalSettingsSetting();
    private Setting globalFanoutSetting = LETool.getGlobalFanoutSetting();
    private Setting convergenceEpsilonSetting = LETool.getConvergenceEpsilonSetting();
    private Setting maxIterationsSetting = LETool.getMaxIterationsSetting();
    private Setting keeperRatioSetting = LETool.getKeeperRatioSetting();

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Logical Effort tab.
	 */
	public void init()
	{
        // tech-independent settings
		leUseLocalSettings.setSelected(getBoolean(useLocalSettingsSetting));
        leGlobalFanOut.setText(getFormattedDouble(globalFanoutSetting));
        leConvergence.setText(getFormattedDouble(convergenceEpsilonSetting));
        leMaxIterations.setText(String.valueOf(getInt(maxIterationsSetting)));
        leKeeperSizeRatio.setText(getFormattedDouble(keeperRatioSetting));

        // tech-dependent settings
		for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
		{
			Technology tech = it.next();
            if (!tech.isLayout()) continue;
			leTechnology.addItem(tech.getTechName());
		}
		leTechnology.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { showArcsInTechnology(); }
		});
		leTechnology.setSelectedItem(Technology.getCurrent().getTechName());

		changingLE = false;
		leGateCapacitance.getDocument().addDocumentListener(new LEDocumentListener(this));
		leDefaultWireCapRatio.getDocument().addDocumentListener(new LEDocumentListener(this));
		leDiffToGateCapRatio.getDocument().addDocumentListener(new LEDocumentListener(this));
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Logical Effort tab.
	 */
	public void term()
	{
        setBoolean(useLocalSettingsSetting, leUseLocalSettings.isSelected());
        setDouble(globalFanoutSetting, TextUtils.atof(leGlobalFanOut.getText()));
        setDouble(convergenceEpsilonSetting, TextUtils.atof(leConvergence.getText()));
        setInt(maxIterationsSetting, Integer.parseInt(leMaxIterations.getText()));
        setDouble(keeperRatioSetting, TextUtils.atof(leKeeperSizeRatio.getText()));
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		// project preferences
        setBoolean(useLocalSettingsSetting, ((Boolean)useLocalSettingsSetting.getFactoryValue()).booleanValue());
        setDouble(globalFanoutSetting, ((Double)globalFanoutSetting.getFactoryValue()).doubleValue());
        setDouble(convergenceEpsilonSetting, ((Double)convergenceEpsilonSetting.getFactoryValue()).doubleValue());
        setInt(maxIterationsSetting, ((Integer)maxIterationsSetting.getFactoryValue()).intValue());
        setDouble(keeperRatioSetting, ((Double)keeperRatioSetting.getFactoryValue()).doubleValue());
		String techName = (String)leTechnology.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech != null)
		{
			setDouble(tech.getGateCapacitanceSetting(), ((Double)tech.getGateCapacitanceSetting().getFactoryValue()).doubleValue());
			setDouble(tech.getWireRatioSetting(), ((Double)tech.getWireRatioSetting().getFactoryValue()).doubleValue());
			setDouble(tech.getDiffAlphaSetting(), ((Double)tech.getDiffAlphaSetting().getFactoryValue()).doubleValue());
		}
	}

	/**
	 * Method called when the user types a new layer name into the edit field.
	 */
	private void leInfoChanged()
	{
		if (changingLE) return;

		String techName = (String)leTechnology.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;

		setDouble(tech.getGateCapacitanceSetting(), TextUtils.atof(leGateCapacitance.getText()));
		setDouble(tech.getWireRatioSetting(), TextUtils.atof(leDefaultWireCapRatio.getText()));
		setDouble(tech.getDiffAlphaSetting(), TextUtils.atof(leDiffToGateCapRatio.getText()));
	}

	/**
	 * Class to handle special changes to changes to a Logical Effort setting.
	 */
	private static class LEDocumentListener implements DocumentListener
	{
		private LogicalEffortTab dialog;

		LEDocumentListener(LogicalEffortTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.leInfoChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.leInfoChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.leInfoChanged(); }
	}

	private void showArcsInTechnology()
	{
		String techName = (String)leTechnology.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;

		changingLE = true;
		leGateCapacitance.setText(getFormattedDouble(tech.getGateCapacitanceSetting()));
		leDefaultWireCapRatio.setText(getFormattedDouble(tech.getWireRatioSetting()));
		leDiffToGateCapRatio.setText(getFormattedDouble(tech.getDiffAlphaSetting()));
		changingLE = false;
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        logicalEffort = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        jLabel15 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        leUseLocalSettings = new javax.swing.JCheckBox();
        leGlobalFanOut = new javax.swing.JTextField();
        leConvergence = new javax.swing.JTextField();
        leMaxIterations = new javax.swing.JTextField();
        leKeeperSizeRatio = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        leDiffToGateCapRatio = new javax.swing.JTextField();
        jLabel22 = new javax.swing.JLabel();
        leDefaultWireCapRatio = new javax.swing.JTextField();
        jLabel20 = new javax.swing.JLabel();
        leGateCapacitance = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        leTechnology = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Tool Options");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        logicalEffort.setLayout(new java.awt.GridBagLayout());

        jLabel4.setText("Global Fan-Out (step-up):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(jLabel4, gridBagConstraints);

        jLabel14.setText("Convergence epsilon:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(jLabel14, gridBagConstraints);

        jLabel15.setText("Maximum number of iterations:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(jLabel15, gridBagConstraints);

        jLabel25.setText("Keeper size ratio (keeper size / driver size):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(jLabel25, gridBagConstraints);

        leUseLocalSettings.setText("Use Local (cell) LE Settings");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 6, 4, 4);
        logicalEffort.add(leUseLocalSettings, gridBagConstraints);

        leGlobalFanOut.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(leGlobalFanOut, gridBagConstraints);

        leConvergence.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(leConvergence, gridBagConstraints);

        leMaxIterations.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(leMaxIterations, gridBagConstraints);

        leKeeperSizeRatio.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(leKeeperSizeRatio, gridBagConstraints);

        jPanel1.setLayout(new java.awt.GridBagLayout());

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Tech-specific"));
        jLabel23.setText("Diffusion to gate cap ratio (alpha) :");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(jLabel23, gridBagConstraints);

        leDiffToGateCapRatio.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(leDiffToGateCapRatio, gridBagConstraints);

        jLabel22.setText("Default wire cap ratio (Cwire / Cgate):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(jLabel22, gridBagConstraints);

        leDefaultWireCapRatio.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(leDefaultWireCapRatio, gridBagConstraints);

        jLabel20.setText("Gate capacitance (fF/Lambda):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(jLabel20, gridBagConstraints);

        leGateCapacitance.setColumns(12);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(leGateCapacitance, gridBagConstraints);

        jLabel1.setText("For Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(leTechnology, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        logicalEffort.add(jPanel1, gridBagConstraints);

        getContentPane().add(logicalEffort, new java.awt.GridBagConstraints());

        jLabel2.setFont(new java.awt.Font("Tahoma", 1, 11));
        jLabel2.setText("All values here are Project Preferences");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
        getContentPane().add(jLabel2, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JTextField leConvergence;
    private javax.swing.JTextField leDefaultWireCapRatio;
    private javax.swing.JTextField leDiffToGateCapRatio;
    private javax.swing.JTextField leGateCapacitance;
    private javax.swing.JTextField leGlobalFanOut;
    private javax.swing.JTextField leKeeperSizeRatio;
    private javax.swing.JTextField leMaxIterations;
    private javax.swing.JComboBox leTechnology;
    private javax.swing.JCheckBox leUseLocalSettings;
    private javax.swing.JPanel logicalEffort;
    // End of variables declaration//GEN-END:variables

}
