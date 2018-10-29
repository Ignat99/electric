/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FastHenryTab.java
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

import com.sun.electric.database.text.TextUtils;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPanel;

/**
 * Class to handle the "FastHenry" tab of the Preferences dialog.
 */
public class FastHenryTab extends PreferencePanel
{
	/** Creates new form FastHenryTab */
	public FastHenryTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(fhFrequencyStart);
	    EDialog.makeTextFieldSelectAllOnTab(fhFrequencyEnd);
	    EDialog.makeTextFieldSelectAllOnTab(fhRunsPerDecade);
	    EDialog.makeTextFieldSelectAllOnTab(fhNumberOfPoles);
	    EDialog.makeTextFieldSelectAllOnTab(fhDefaultThickness);
	    EDialog.makeTextFieldSelectAllOnTab(fhDefaultWidthSubs);
	    EDialog.makeTextFieldSelectAllOnTab(fhDefaultHeightSubs);
	    EDialog.makeTextFieldSelectAllOnTab(fhMaxSegmentLength);
	}

	/** return the panel to use for user preferences. */
	public JPanel getUserPreferencesPanel() { return fastHenry; }

	/** return the name of this preferences tab. */
	public String getName() { return "Fast Henry"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Fast Henry tab.
	 */
	public void init()
	{
		fhUseSingleFrequency.setSelected(SimulationTool.isFastHenryUseSingleFrequency());
		fhUseSingleFrequency.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { updateWhatIsEnabled(); }
		});

		fhFrequencyStart.setText(TextUtils.formatDouble(SimulationTool.getFastHenryStartFrequency()));
		fhFrequencyEnd.setText(TextUtils.formatDouble(SimulationTool.getFastHenryEndFrequency()));
		fhRunsPerDecade.setText(Integer.toString(SimulationTool.getFastHenryRunsPerDecade()));
		fhMakeMultipole.setSelected(SimulationTool.isFastHenryMultiPole());
		fhMakeMultipole.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { updateWhatIsEnabled(); }
		});

		fhNumberOfPoles.setText(Integer.toString(SimulationTool.getFastHenryNumPoles()));
		fhDefaultThickness.setText(TextUtils.formatDistance(SimulationTool.getFastHenryDefThickness()));
		fhDefaultWidthSubs.setText(Integer.toString(SimulationTool.getFastHenryWidthSubdivisions()));
		fhDefaultHeightSubs.setText(Integer.toString(SimulationTool.getFastHenryHeightSubdivisions()));
		fhMaxSegmentLength.setText(TextUtils.formatDistance(SimulationTool.getFastHenryMaxSegLength()));

		updateWhatIsEnabled();
	}

	private void updateWhatIsEnabled()
	{
		fhFrequencyEnd.setEnabled(!fhUseSingleFrequency.isSelected());
		fhFreqEndLabel.setEnabled(!fhUseSingleFrequency.isSelected());
		fhRunsPerDecade.setEnabled(!fhUseSingleFrequency.isSelected());
		fhRunsPerDecadeLabel.setEnabled(!fhUseSingleFrequency.isSelected());
		fhNumberOfPoles.setEnabled(fhMakeMultipole.isSelected());
		fhNumPolesLabel.setEnabled(fhMakeMultipole.isSelected());
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Fast Henry tab.
	 */
	public void term()
	{
		boolean currBoolean = fhUseSingleFrequency.isSelected();
		if (currBoolean != SimulationTool.isFastHenryUseSingleFrequency())
			SimulationTool.setFastHenryUseSingleFrequency(currBoolean);

		double currDouble = TextUtils.atof(fhFrequencyStart.getText());
		if (currDouble != SimulationTool.getFastHenryStartFrequency())
			SimulationTool.setFastHenryStartFrequency(currDouble);

		currDouble = TextUtils.atof(fhFrequencyEnd.getText());
		if (currDouble != SimulationTool.getFastHenryEndFrequency())
			SimulationTool.setFastHenryEndFrequency(currDouble);

		int currInt = TextUtils.atoi(fhRunsPerDecade.getText());
		if (currInt != SimulationTool.getFastHenryRunsPerDecade())
			SimulationTool.setFastHenryRunsPerDecade(currInt);

		currBoolean = fhMakeMultipole.isSelected();
		if (currBoolean != SimulationTool.isFastHenryMultiPole())
			SimulationTool.setFastHenryMultiPole(currBoolean);

		currInt = TextUtils.atoi(fhNumberOfPoles.getText());
		if (currInt != SimulationTool.getFastHenryNumPoles())
			SimulationTool.setFastHenryNumPoles(currInt);

		currDouble = TextUtils.atofDistance(fhDefaultThickness.getText());
		if (currDouble != SimulationTool.getFastHenryDefThickness())
			SimulationTool.setFastHenryDefThickness(currDouble);

		currInt = TextUtils.atoi(fhDefaultWidthSubs.getText());
		if (currInt != SimulationTool.getFastHenryWidthSubdivisions())
			SimulationTool.setFastHenryWidthSubdivisions(currInt);

		currInt = TextUtils.atoi(fhDefaultHeightSubs.getText());
		if (currInt != SimulationTool.getFastHenryHeightSubdivisions())
			SimulationTool.setFastHenryHeightSubdivisions(currInt);

		currDouble = TextUtils.atofDistance(fhMaxSegmentLength.getText());
		if (currDouble != SimulationTool.getFastHenryMaxSegLength())
			SimulationTool.setFastHenryMaxSegLength(currDouble);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		if (SimulationTool.isFactoryFastHenryUseSingleFrequency() != SimulationTool.isFastHenryUseSingleFrequency())
			SimulationTool.setFastHenryUseSingleFrequency(SimulationTool.isFactoryFastHenryUseSingleFrequency());
		if (SimulationTool.getFactoryFastHenryDefThickness() != SimulationTool.getFastHenryDefThickness())
			SimulationTool.setFastHenryDefThickness(SimulationTool.getFactoryFastHenryDefThickness());

		if (SimulationTool.getFactoryFastHenryStartFrequency() != SimulationTool.getFastHenryStartFrequency())
			SimulationTool.setFastHenryStartFrequency(SimulationTool.getFactoryFastHenryStartFrequency());
		if (SimulationTool.getFactoryFastHenryEndFrequency() != SimulationTool.getFastHenryEndFrequency())
			SimulationTool.setFastHenryEndFrequency(SimulationTool.getFactoryFastHenryEndFrequency());
		if (SimulationTool.getFactoryFastHenryRunsPerDecade() != SimulationTool.getFastHenryRunsPerDecade())
			SimulationTool.setFastHenryRunsPerDecade(SimulationTool.getFactoryFastHenryRunsPerDecade());

		if (SimulationTool.getFactoryFastHenryWidthSubdivisions() != SimulationTool.getFastHenryWidthSubdivisions())
			SimulationTool.setFastHenryWidthSubdivisions(SimulationTool.getFactoryFastHenryWidthSubdivisions());
		if (SimulationTool.getFactoryFastHenryHeightSubdivisions() != SimulationTool.getFastHenryHeightSubdivisions())
			SimulationTool.setFastHenryHeightSubdivisions(SimulationTool.getFactoryFastHenryHeightSubdivisions());
		if (SimulationTool.getFactoryFastHenryMaxSegLength() != SimulationTool.getFastHenryMaxSegLength())
			SimulationTool.setFastHenryMaxSegLength(SimulationTool.getFactoryFastHenryMaxSegLength());

		if (SimulationTool.isFactoryFastHenryMultiPole() != SimulationTool.isFastHenryMultiPole())
			SimulationTool.setFastHenryMultiPole(SimulationTool.isFactoryFastHenryMultiPole());
		if (SimulationTool.getFactoryFastHenryNumPoles() != SimulationTool.getFastHenryNumPoles())
			SimulationTool.setFastHenryNumPoles(SimulationTool.getFactoryFastHenryNumPoles());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    private void initComponents()//GEN-BEGIN:initComponents
    {
        java.awt.GridBagConstraints gridBagConstraints;

        fastHenry = new javax.swing.JPanel();
        fhUseSingleFrequency = new javax.swing.JCheckBox();
        jLabel55 = new javax.swing.JLabel();
        fhFreqEndLabel = new javax.swing.JLabel();
        fhRunsPerDecadeLabel = new javax.swing.JLabel();
        fhMakeMultipole = new javax.swing.JCheckBox();
        fhNumPolesLabel = new javax.swing.JLabel();
        fhFrequencyStart = new javax.swing.JTextField();
        fhFrequencyEnd = new javax.swing.JTextField();
        fhRunsPerDecade = new javax.swing.JTextField();
        fhNumberOfPoles = new javax.swing.JTextField();
        jLabel59 = new javax.swing.JLabel();
        jLabel60 = new javax.swing.JLabel();
        jLabel61 = new javax.swing.JLabel();
        jLabel62 = new javax.swing.JLabel();
        fhDefaultThickness = new javax.swing.JTextField();
        fhDefaultWidthSubs = new javax.swing.JTextField();
        fhDefaultHeightSubs = new javax.swing.JTextField();
        fhMaxSegmentLength = new javax.swing.JTextField();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Tool Options");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });

        fastHenry.setLayout(new java.awt.GridBagLayout());

        fhUseSingleFrequency.setText("Use single frequency");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhUseSingleFrequency, gridBagConstraints);

        jLabel55.setText("Frequency start:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 20, 4, 4);
        fastHenry.add(jLabel55, gridBagConstraints);

        fhFreqEndLabel.setText("Frequency end:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 20, 4, 4);
        fastHenry.add(fhFreqEndLabel, gridBagConstraints);

        fhRunsPerDecadeLabel.setText("Runs per decade:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 20, 4, 4);
        fastHenry.add(fhRunsPerDecadeLabel, gridBagConstraints);

        fhMakeMultipole.setText("Make multipole subcircuit");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhMakeMultipole, gridBagConstraints);

        fhNumPolesLabel.setText("Number of poles:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 20, 4, 4);
        fastHenry.add(fhNumPolesLabel, gridBagConstraints);

        fhFrequencyStart.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhFrequencyStart, gridBagConstraints);

        fhFrequencyEnd.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhFrequencyEnd, gridBagConstraints);

        fhRunsPerDecade.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhRunsPerDecade, gridBagConstraints);

        fhNumberOfPoles.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhNumberOfPoles, gridBagConstraints);

        jLabel59.setText("Default thickness:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(jLabel59, gridBagConstraints);

        jLabel60.setText("Default width subdivisions:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(jLabel60, gridBagConstraints);

        jLabel61.setText("Default height subdivisions:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(jLabel61, gridBagConstraints);

        jLabel62.setText("Maximum segment length:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(jLabel62, gridBagConstraints);

        fhDefaultThickness.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhDefaultThickness, gridBagConstraints);

        fhDefaultWidthSubs.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhDefaultWidthSubs, gridBagConstraints);

        fhDefaultHeightSubs.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhDefaultHeightSubs, gridBagConstraints);

        fhMaxSegmentLength.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        fastHenry.add(fhMaxSegmentLength, gridBagConstraints);

        getContentPane().add(fastHenry, new java.awt.GridBagConstraints());

        pack();
    }//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel fastHenry;
    private javax.swing.JTextField fhDefaultHeightSubs;
    private javax.swing.JTextField fhDefaultThickness;
    private javax.swing.JTextField fhDefaultWidthSubs;
    private javax.swing.JLabel fhFreqEndLabel;
    private javax.swing.JTextField fhFrequencyEnd;
    private javax.swing.JTextField fhFrequencyStart;
    private javax.swing.JCheckBox fhMakeMultipole;
    private javax.swing.JTextField fhMaxSegmentLength;
    private javax.swing.JLabel fhNumPolesLabel;
    private javax.swing.JTextField fhNumberOfPoles;
    private javax.swing.JTextField fhRunsPerDecade;
    private javax.swing.JLabel fhRunsPerDecadeLabel;
    private javax.swing.JCheckBox fhUseSingleFrequency;
    private javax.swing.JLabel jLabel55;
    private javax.swing.JLabel jLabel59;
    private javax.swing.JLabel jLabel60;
    private javax.swing.JLabel jLabel61;
    private javax.swing.JLabel jLabel62;
    // End of variables declaration//GEN-END:variables

}
