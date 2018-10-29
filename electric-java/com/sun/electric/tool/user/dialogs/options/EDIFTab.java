/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EDIFTab.java
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

import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.util.TextUtils;

import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * Class to handle the "EDIF" tab of the Preferences dialog.
 */
public class EDIFTab extends PreferencePanel
{
	private JTextArea textArea;

	/** Creates new form EDIFTab */
	public EDIFTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();
		textArea = new JTextArea();
		acceptedParamPane.setViewportView(textArea);

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(edifInputScale);
	    EDialog.makeTextFieldSelectAllOnTab(edifConfigFile);
	}

	/** return the panel to use for user preferences. */
	public JPanel getUserPreferencesPanel() { return edif; }

	/** return the name of this preferences tab. */
	public String getName() { return "EDIF"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the EDIF tab.
	 */
	public void init()
	{
		edifUseSchematicView.setSelected(IOTool.isEDIFUseSchematicView());
		edifInputScale.setText(TextUtils.formatDouble(IOTool.getEDIFInputScale()));
		edifImportStitchesCells.setSelected(IOTool.isEDIFImportStitchesCells());
		edifCadenceCompatibility.setSelected(IOTool.isEDIFCadenceCompatibility());
		edifShowArcNames.setSelected(IOTool.isEDIFShowArcNames());
		edifShowNodeNames.setSelected(IOTool.isEDIFShowNodeNames());
		edifConfigFile.setText(IOTool.getEDIFConfigurationFile());
		String ap = IOTool.getEDIFAcceptedParameters();
		String [] params = ap.split("/");
		textArea.removeAll();
		for(int i=0; i<params.length; i++)
			textArea.append(params[i] + "\n");
		if (IOTool.isEDIFImportAllParameters())
			allParameters.setSelected(true);
		else
			selectParameters.setSelected(true);
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the EDIF tab.
	 */
	public void term()
	{
		boolean currentBoolean = edifUseSchematicView.isSelected();
		if (currentBoolean != IOTool.isEDIFUseSchematicView())
			IOTool.setEDIFUseSchematicView(currentBoolean);

		double currentInputScale = TextUtils.atof(edifInputScale.getText());
		if (currentInputScale != IOTool.getEDIFInputScale())
			IOTool.setEDIFInputScale(currentInputScale);

		currentBoolean = edifImportStitchesCells.isSelected();
		if (currentBoolean != IOTool.isEDIFImportStitchesCells())
			IOTool.setEDIFImportStitchesCells(currentBoolean);

		currentBoolean = edifCadenceCompatibility.isSelected();
		if (currentBoolean != IOTool.isEDIFCadenceCompatibility())
			IOTool.setEDIFCadenceCompatibility(currentBoolean);

		currentBoolean = edifShowArcNames.isSelected();
		if (currentBoolean != IOTool.isEDIFShowArcNames())
			IOTool.setEDIFShowArcNames(currentBoolean);

		currentBoolean = edifShowNodeNames.isSelected();
		if (currentBoolean != IOTool.isEDIFShowNodeNames())
			IOTool.setEDIFShowNodeNames(currentBoolean);

		String currentConfigFile = edifConfigFile.getText();
		if (!currentConfigFile.equals(IOTool.getEDIFConfigurationFile()))
			IOTool.setEDIFConfigurationFile(currentConfigFile);

		StringBuffer ap = new StringBuffer();
		String allAP = textArea.getText();
		String [] params = allAP.split("\n");
		for(int i=0; i<params.length; i++)
		{
			String paramName = params[i].trim();
			if (paramName.length() == 0) continue;
			if (ap.length() > 0) ap.append("/");
			ap.append(paramName);
		}
		String currentAcceptedParams = ap.toString();
		if (!currentAcceptedParams.equals(IOTool.getEDIFAcceptedParameters()))
			IOTool.setEDIFAcceptedParameters(currentAcceptedParams);
		// priority with AllParameters option. It will ignore the list
		currentBoolean = allParameters.isSelected();
		if (currentBoolean != IOTool.isEDIFImportAllParameters())
			IOTool.setEDIFImportAllParameters(currentBoolean);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		if (IOTool.isFactoryEDIFUseSchematicView() != IOTool.isEDIFUseSchematicView())
			IOTool.setEDIFUseSchematicView(IOTool.isFactoryEDIFUseSchematicView());
		if (IOTool.getFactoryEDIFInputScale() != IOTool.getEDIFInputScale())
			IOTool.setEDIFInputScale(IOTool.getFactoryEDIFInputScale());
		if (IOTool.isFactoryEDIFImportStitchesCells() != IOTool.isEDIFImportStitchesCells())
			IOTool.setEDIFImportStitchesCells(IOTool.isFactoryEDIFImportStitchesCells());
		if (IOTool.isFactoryEDIFCadenceCompatibility() != IOTool.isEDIFCadenceCompatibility())
			IOTool.setEDIFCadenceCompatibility(IOTool.isFactoryEDIFCadenceCompatibility());
		if (IOTool.isFactoryEDIFShowArcNames() != IOTool.isEDIFShowArcNames())
			IOTool.setEDIFShowArcNames(IOTool.isFactoryEDIFShowArcNames());
		if (IOTool.isFactoryEDIFShowNodeNames() != IOTool.isEDIFShowNodeNames())
			IOTool.setEDIFShowNodeNames(IOTool.isFactoryEDIFShowNodeNames());
		if (!IOTool.getFactoryEDIFConfigurationFile().equals(IOTool.getEDIFConfigurationFile()))
			IOTool.setEDIFConfigurationFile(IOTool.getFactoryEDIFConfigurationFile());
		if (!IOTool.getFactoryEDIFAcceptedParameters().equals(IOTool.getEDIFAcceptedParameters()))
			IOTool.setEDIFAcceptedParameters(IOTool.getFactoryEDIFAcceptedParameters());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        buttonGroup1 = new javax.swing.ButtonGroup();
        edif = new javax.swing.JPanel();
        edifUseSchematicView = new javax.swing.JCheckBox();
        jLabel13 = new javax.swing.JLabel();
        edifInputScale = new javax.swing.JTextField();
        edifCadenceCompatibility = new javax.swing.JCheckBox();
        jLabel4 = new javax.swing.JLabel();
        configFile = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        edifConfigFile = new javax.swing.JTextField();
        edifBrowse = new javax.swing.JButton();
        acceptedParameters = new javax.swing.JPanel();
        selectParameters = new javax.swing.JRadioButton();
        allParameters = new javax.swing.JRadioButton();
        jPanel1 = new javax.swing.JPanel();
        acceptedParamPane = new javax.swing.JScrollPane();
        jLabel1 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        edifShowArcNames = new javax.swing.JCheckBox();
        edifShowNodeNames = new javax.swing.JCheckBox();
        edifImportStitchesCells = new javax.swing.JCheckBox();

        setTitle("IO Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        edif.setLayout(new java.awt.GridBagLayout());

        edifUseSchematicView.setText("Use Schematic View when writing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        edif.add(edifUseSchematicView, gridBagConstraints);

        jLabel13.setText("Scale by:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        edif.add(jLabel13, gridBagConstraints);

        edifInputScale.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        edif.add(edifInputScale, gridBagConstraints);

        edifCadenceCompatibility.setText("Cadence compatibility");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        edif.add(edifCadenceCompatibility, gridBagConstraints);

        jLabel4.setText("when reading");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        edif.add(jLabel4, gridBagConstraints);

        configFile.setBorder(javax.swing.BorderFactory.createTitledBorder("Configuration File"));
        configFile.setLayout(new java.awt.GridBagLayout());

        jLabel3.setText("EDIF reading and writing.");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 4);
        configFile.add(jLabel3, gridBagConstraints);

        jLabel2.setText("The configuration file provides overrides for controlling");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
        configFile.add(jLabel2, gridBagConstraints);

        edifConfigFile.setMinimumSize(new java.awt.Dimension(200, 20));
        edifConfigFile.setPreferredSize(new java.awt.Dimension(200, 20));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(1, 4, 4, 4);
        configFile.add(edifConfigFile, gridBagConstraints);

        edifBrowse.setText("Browse...");
        edifBrowse.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                edifBrowseActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
        configFile.add(edifBrowse, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        edif.add(configFile, gridBagConstraints);

        acceptedParameters.setBorder(javax.swing.BorderFactory.createTitledBorder("Accepted Parameters"));
        acceptedParameters.setLayout(new java.awt.GridBagLayout());

        buttonGroup1.add(selectParameters);
        selectParameters.setText("Parameters List");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        acceptedParameters.add(selectParameters, gridBagConstraints);

        buttonGroup1.add(allParameters);
        allParameters.setText("All Parameters");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        acceptedParameters.add(allParameters, gridBagConstraints);

        jPanel1.setLayout(new java.awt.GridBagLayout());

        acceptedParamPane.setMinimumSize(new java.awt.Dimension(150, 100));
        acceptedParamPane.setPreferredSize(new java.awt.Dimension(150, 100));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
        jPanel1.add(acceptedParamPane, gridBagConstraints);

        jLabel1.setText("Type parameter names (one per line)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        jPanel1.add(jLabel1, gridBagConstraints);

        jLabel5.setText("that will be placed on nodes when reading EDIF");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 8, 4, 0);
        jPanel1.add(jLabel5, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        acceptedParameters.add(jPanel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        edif.add(acceptedParameters, gridBagConstraints);

        edifShowArcNames.setText("Show arc names");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        edif.add(edifShowArcNames, gridBagConstraints);

        edifShowNodeNames.setText("Show node names");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        edif.add(edifShowNodeNames, gridBagConstraints);

        edifImportStitchesCells.setText("Stitch cells when reading");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        edif.add(edifImportStitchesCells, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(edif, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void edifBrowseActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_edifBrowseActionPerformed
	{//GEN-HEADEREND:event_edifBrowseActionPerformed
		String fileName = OpenFile.chooseInputFile(FileType.ANY, null, null);
		if (fileName == null) return;
		edifConfigFile.setText(fileName);
	}//GEN-LAST:event_edifBrowseActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane acceptedParamPane;
    private javax.swing.JPanel acceptedParameters;
    private javax.swing.JRadioButton allParameters;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JPanel configFile;
    private javax.swing.JPanel edif;
    private javax.swing.JButton edifBrowse;
    private javax.swing.JCheckBox edifCadenceCompatibility;
    private javax.swing.JTextField edifConfigFile;
    private javax.swing.JCheckBox edifImportStitchesCells;
    private javax.swing.JTextField edifInputScale;
    private javax.swing.JCheckBox edifShowArcNames;
    private javax.swing.JCheckBox edifShowNodeNames;
    private javax.swing.JCheckBox edifUseSchematicView;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButton selectParameters;
    // End of variables declaration//GEN-END:variables

}
