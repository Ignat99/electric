/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DEFTab.java
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

import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;

import javax.swing.JPanel;

/**
 * Class to handle the "DEF" tab of the Preferences dialog.
 */
public class DEFTab extends PreferencePanel
{
	/** Creates new form DEFTab */
	public DEFTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();
	}

	/** return the panel to use for the user preferences. */
	public JPanel getUserPreferencesPanel() { return def; }

	/** return the name of this preferences tab. */
	public String getName() { return "DEF"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the DEF tab.
	 */
	public void init()
	{
		defPlacePhysical.setSelected(IOTool.isDEFPhysicalPlacement());
		defIgnorePhysInNets.setSelected(IOTool.isDEFIgnorePhysicalInNets());
		defUsePureLayerNodes.setSelected(IOTool.isDEFUsePureLayerNodes());
		defPlaceLogical.setSelected(IOTool.isDEFLogicalPlacement());
		defIgnoreLogInSpecialNets.setSelected(IOTool.isDEFIgnoreLogicalInSpecialNets());
		defMakeDummyCells.setSelected(IOTool.isDEFMakeDummyCells());
		defIgnoreUngeneratedPins.setSelected(IOTool.isDEFIgnoreUngeneratedPins());
		defIgnoreViasBlock.setSelected(IOTool.isDEFIgnoreViasBlock());
		defConnectAllPins.setSelected(IOTool.isDEFPlaceAndConnectAllPins());
		
		defUnknownLayers.addItem("Ignore");
		defUnknownLayers.addItem("Convert to DRC Exclusion layer");
		defUnknownLayers.setSelectedIndex(IOTool.getDEFInUnknownLayerHandling());
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the DEF tab.
	 */
	public void term()
	{
		boolean currentValue = defPlacePhysical.isSelected();
		if (currentValue != IOTool.isDEFPhysicalPlacement())
			IOTool.setDEFPhysicalPlacement(currentValue);

		currentValue = defIgnorePhysInNets.isSelected();
		if (currentValue != IOTool.isDEFIgnorePhysicalInNets())
			IOTool.setDEFIgnorePhysicalInNets(currentValue);

		currentValue = defUsePureLayerNodes.isSelected();
		if (currentValue != IOTool.isDEFUsePureLayerNodes())
			IOTool.setDEFUsePureLayerNodes(currentValue);

		currentValue = defPlaceLogical.isSelected();
		if (currentValue != IOTool.isDEFLogicalPlacement())
			IOTool.setDEFLogicalPlacement(currentValue);

		currentValue = defIgnoreLogInSpecialNets.isSelected();
		if (currentValue != IOTool.isDEFIgnoreLogicalInSpecialNets())
			IOTool.setDEFIgnoreLogicalInSpecialNets(currentValue);

		currentValue = defMakeDummyCells.isSelected();
		if (currentValue != IOTool.isDEFMakeDummyCells())
			IOTool.setDEFMakeDummyCells(currentValue);

		currentValue = defIgnoreUngeneratedPins.isSelected();
		if (currentValue != IOTool.isDEFIgnoreUngeneratedPins())
			IOTool.setDEFIgnoreUngeneratedPins(currentValue);
		
		currentValue = defIgnoreViasBlock.isSelected();
		if (currentValue != IOTool.isDEFIgnoreViasBlock())
			IOTool.setDEFIgnoreViasBlock(currentValue);
		
		int currentI = defUnknownLayers.getSelectedIndex();
		if (currentI != IOTool.getDEFInUnknownLayerHandling())
			IOTool.setDEFInUnknownLayerHandling(currentI);
		
		currentValue = defConnectAllPins.isSelected();
		if (currentValue != IOTool.isDEFPlaceAndConnectAllPins())
			IOTool.setDEFPlaceAndConnectAllPins(currentValue);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		if (IOTool.isFactoryDEFPhysicalPlacement() != IOTool.isDEFPhysicalPlacement())
			IOTool.setDEFPhysicalPlacement(IOTool.isFactoryDEFPhysicalPlacement());
		if (IOTool.isFactoryDEFIgnorePhysicalInNets() != IOTool.isDEFIgnorePhysicalInNets())
			IOTool.setDEFIgnorePhysicalInNets(IOTool.isFactoryDEFIgnorePhysicalInNets());
		if (IOTool.isFactoryDEFUsePureLayerNodes() != IOTool.isDEFUsePureLayerNodes())
			IOTool.setDEFUsePureLayerNodes(IOTool.isFactoryDEFUsePureLayerNodes());
		if (IOTool.isFactoryDEFLogicalPlacement() != IOTool.isDEFLogicalPlacement())
			IOTool.setDEFLogicalPlacement(IOTool.isFactoryDEFLogicalPlacement());
		if (IOTool.isFactoryDEFIgnoreLogicalInSpecialNets() != IOTool.isDEFIgnoreLogicalInSpecialNets())
			IOTool.setDEFIgnoreLogicalInSpecialNets(IOTool.isFactoryDEFIgnoreLogicalInSpecialNets());
		if (IOTool.isFactoryDEFMakeDummyCells() != IOTool.isDEFMakeDummyCells())
			IOTool.setDEFMakeDummyCells(IOTool.isFactoryDEFMakeDummyCells());
		if (IOTool.isFactoryDEFIgnoreUngeneratedPins() != IOTool.isDEFIgnoreUngeneratedPins())
			IOTool.setDEFIgnoreUngeneratedPins(IOTool.isFactoryDEFIgnoreUngeneratedPins());
		if (IOTool.getFactoryDEFInUnknownLayerHandling() != IOTool.getDEFInUnknownLayerHandling())
			IOTool.setDEFInUnknownLayerHandling(IOTool.getFactoryDEFInUnknownLayerHandling());
		if (IOTool.isFactoryDEFPlaceAndConnectAllPins() != IOTool.isDEFPlaceAndConnectAllPins())
			IOTool.setDEFPlaceAndConnectAllPins(IOTool.isFactoryDEFPlaceAndConnectAllPins());
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

        def = new javax.swing.JPanel();
        defPlacePhysical = new javax.swing.JCheckBox();
        defPlaceLogical = new javax.swing.JCheckBox();
        defUsePureLayerNodes = new javax.swing.JCheckBox();
        defIgnoreLogInSpecialNets = new javax.swing.JCheckBox();
        defIgnorePhysInNets = new javax.swing.JCheckBox();
        defMakeDummyCells = new javax.swing.JCheckBox();
        defIgnoreUngeneratedPins = new javax.swing.JCheckBox();
        defIgnoreViasBlock = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        defUnknownLayers = new javax.swing.JComboBox();
        defConnectAllPins = new javax.swing.JCheckBox();

        setTitle("IO Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        def.setBorder(javax.swing.BorderFactory.createTitledBorder("Import"));
        def.setLayout(new java.awt.GridBagLayout());

        defPlacePhysical.setText("Place physical interconnect");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        def.add(defPlacePhysical, gridBagConstraints);

        defPlaceLogical.setText("Place logical interconnect");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 1, 4);
        def.add(defPlaceLogical, gridBagConstraints);

        defUsePureLayerNodes.setText("Use pure-layer nodes instead of arcs");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 4, 4);
        def.add(defUsePureLayerNodes, gridBagConstraints);

        defIgnoreLogInSpecialNets.setText("Ignore logical interconnect in SPECIALNETS section");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 4, 4);
        def.add(defIgnoreLogInSpecialNets, gridBagConstraints);

        defIgnorePhysInNets.setText("Ignore physical  interconnect in NETS section");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 20, 1, 4);
        def.add(defIgnorePhysInNets, gridBagConstraints);

        defMakeDummyCells.setText("Make dummy cells for unknown cells");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        def.add(defMakeDummyCells, gridBagConstraints);

        defIgnoreUngeneratedPins.setText("Ignore ungenerated pins (with no location)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        def.add(defIgnoreUngeneratedPins, gridBagConstraints);

        defIgnoreViasBlock.setText("Ignore vias block");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        def.add(defIgnoreViasBlock, gridBagConstraints);

        jLabel3.setText("Unknown layers:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        def.add(jLabel3, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        def.add(defUnknownLayers, gridBagConstraints);

        defConnectAllPins.setText("Place and connect all pin geometry");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        def.add(defConnectAllPins, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        getContentPane().add(def, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel def;
    private javax.swing.JCheckBox defConnectAllPins;
    private javax.swing.JCheckBox defIgnoreLogInSpecialNets;
    private javax.swing.JCheckBox defIgnorePhysInNets;
    private javax.swing.JCheckBox defIgnoreUngeneratedPins;
    private javax.swing.JCheckBox defIgnoreViasBlock;
    private javax.swing.JCheckBox defMakeDummyCells;
    private javax.swing.JCheckBox defPlaceLogical;
    private javax.swing.JCheckBox defPlacePhysical;
    private javax.swing.JComboBox defUnknownLayers;
    private javax.swing.JCheckBox defUsePureLayerNodes;
    private javax.swing.JLabel jLabel3;
    // End of variables declaration//GEN-END:variables

}
