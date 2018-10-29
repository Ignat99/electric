/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SkillTab.java
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

import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;

import java.util.Iterator;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "Skill" tab of the Preferences dialog.
 */
public class SkillTab extends PreferencePanel
{
	private JList skillLayerList;
	private DefaultListModel skillLayerModel;

	/** Creates new form SkillTab */
	public SkillTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(skillLayerName);
	}

	/** return the JPanel to use for the user preferences. */
	public JPanel getUserPreferencesPanel() { return preferences; }

	/** return the JPanel to use for the project preferences. */
	public JPanel getProjectPreferencesPanel() { return projectSettings; }

	/** return the name of this preferences tab. */
	public String getName() { return "Skill"; }

	private void loadLayers()
	{
		if (skillLayerModel == null) return; // nothing to do
		skillLayerModel.clear();
		skillTechnology.setText("Skill layers for technology: " + curTech.getTechName());
		List<Layer> list = curTech.getLayersSortedByUserPreference();
		for (Layer layer : list)
		{
			String skillLayerName = getString(layer.getSkillLayerSetting());
			skillLayerModel.addElement(layer.getName() + " (" + skillLayerName + ")");
		}
	}
	
	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Skill tab.
	 */
	public void init()
	{
		// project preferences
		skillLayerModel = new DefaultListModel();
		skillLayerList = new JList(skillLayerModel);
		skillLayerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		skillLayerPane.setViewportView(skillLayerList);
		skillLayerList.clearSelection();
		skillLayerList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { skillClickLayer(); }
		});
		skillTechnology.setText("Skill layers for technology: " + curTech.getTechName());
		loadLayers();
//		skillLayerModel.clear();
//		List<Layer> list = curTech.getLayersSortedByUserPreference();
//		for (Layer layer : list)
//		{
//			String skillLayerName = getString(layer.getSkillLayerSetting());
//			skillLayerModel.addElement(layer.getName() + " (" + skillLayerName + ")");
//		}
		skillLayerList.setSelectedIndex(0);
		skillClickLayer();
		skillLayerName.getDocument().addDocumentListener(new LayerDocumentListener(this));

		// user preferences
		skillNoSubCells.setSelected(IOTool.isSkillExcludesSubcells());
		skillFlattenHierarchy.setSelected(IOTool.isSkillFlattensHierarchy());
        skillGDSNameLimit.setSelected(IOTool.isSkillGDSNameLimit());
		if (!IOTool.hasSkill())
		{
			skillNoSubCells.setEnabled(false);
			skillFlattenHierarchy.setEnabled(false);
	        skillGDSNameLimit.setEnabled(false);
		}
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Skill tab.
	 */
	public void term()
	{
		boolean currBoolean = skillNoSubCells.isSelected();
		if (currBoolean != IOTool.isSkillExcludesSubcells())
			IOTool.setSkillExcludesSubcells(currBoolean);

		currBoolean = skillFlattenHierarchy.isSelected();
		if (currBoolean !=  IOTool.isSkillFlattensHierarchy())
			IOTool.setSkillFlattensHierarchy(currBoolean);

        currBoolean = skillGDSNameLimit.isSelected();
        if (currBoolean != IOTool.isSkillGDSNameLimit())
            IOTool.setSkillGDSNameLimit(currBoolean);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		// user preferences
		if (IOTool.isFactorySkillExcludesSubcells() != IOTool.isSkillExcludesSubcells())
			IOTool.setSkillExcludesSubcells(IOTool.isFactorySkillExcludesSubcells());
		if (IOTool.isFactorySkillFlattensHierarchy() != IOTool.isSkillFlattensHierarchy())
			IOTool.setSkillFlattensHierarchy(IOTool.isFactorySkillFlattensHierarchy());
		if (IOTool.isFactorySkillGDSNameLimit() != IOTool.isSkillGDSNameLimit())
			IOTool.setSkillGDSNameLimit(IOTool.isFactorySkillGDSNameLimit());

		// project preferences
        for(Iterator<Layer> it = curTech.getLayers(); it.hasNext(); )
        {
        	Layer layer = it.next();
        	Setting set = layer.getSkillLayerSetting();
        	Object factoryObj = set.getFactoryValue();
        	if (factoryObj instanceof String)
        		setString(set, (String)factoryObj);
        }
	}

    /**
     * Method called when other preferences tab changed the layers sorting algorithm
     */
    @Override
    public void refresh()
    {
    	loadLayers();
    }
    
	/**
	 * Class to handle special changes to changes to a Technology in the Skill panel.
	 */
	private static class LayerDocumentListener implements DocumentListener
	{
		SkillTab dialog;

		LayerDocumentListener(SkillTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.skillLayerChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.skillLayerChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.skillLayerChanged(); }
	}

	/**
	 * Method called when the user types a new value into the Skill layer field.
	 */
	private void skillLayerChanged()
	{
		String str = (String)skillLayerList.getSelectedValue();
		int spacePos = str.indexOf(" ");
		if (spacePos >= 0) str = str.substring(0, spacePos);
		Layer layer = curTech.findLayer(str);
		if (layer == null) return;

		String layerName = skillLayerName.getText();
		setString(layer.getSkillLayerSetting(), layerName);
		String newLine = layer.getName() + " (" + layerName + ")";
		int index = skillLayerList.getSelectedIndex();
		skillLayerModel.set(index, newLine);
	}

	/**
	 * Method called when the user clicks on a layer name in the scrollable list.
	 */
	private void skillClickLayer()
	{
		String str = (String)skillLayerList.getSelectedValue();
		if (str == null) return;
		
		int spacePos = str.indexOf(" ");
		if (spacePos >= 0) str = str.substring(0, spacePos);
		Layer layer = curTech.findLayer(str);
		if (layer == null) return;
		skillLayerName.setText(getString(layer.getSkillLayerSetting()));
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jSeparator1 = new javax.swing.JSeparator();
        projectSettings = new javax.swing.JPanel();
        skillLayerPane = new javax.swing.JScrollPane();
        skillLayerName = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        skillTechnology = new javax.swing.JLabel();
        preferences = new javax.swing.JPanel();
        skillNoSubCells = new javax.swing.JCheckBox();
        skillFlattenHierarchy = new javax.swing.JCheckBox();
        skillGDSNameLimit = new javax.swing.JCheckBox();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("IO Options");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(jSeparator1, gridBagConstraints);

        projectSettings.setLayout(new java.awt.GridBagLayout());

        skillLayerPane.setMinimumSize(new java.awt.Dimension(150, 150));
        skillLayerPane.setPreferredSize(new java.awt.Dimension(150, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        projectSettings.add(skillLayerPane, gridBagConstraints);

        skillLayerName.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        projectSettings.add(skillLayerName, gridBagConstraints);

        jLabel11.setText("SKILL Layer:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        projectSettings.add(jLabel11, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        projectSettings.add(skillTechnology, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(projectSettings, gridBagConstraints);

        preferences.setLayout(new java.awt.GridBagLayout());

        skillNoSubCells.setText("Do not include subcells");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        preferences.add(skillNoSubCells, gridBagConstraints);

        skillFlattenHierarchy.setText("Flatten hierarchy");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        preferences.add(skillFlattenHierarchy, gridBagConstraints);

        skillGDSNameLimit.setText("GDS name limit (32 chars)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        preferences.add(skillGDSNameLimit, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(preferences, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel11;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JPanel preferences;
    private javax.swing.JPanel projectSettings;
    private javax.swing.JCheckBox skillFlattenHierarchy;
    private javax.swing.JCheckBox skillGDSNameLimit;
    private javax.swing.JTextField skillLayerName;
    private javax.swing.JScrollPane skillLayerPane;
    private javax.swing.JCheckBox skillNoSubCells;
    private javax.swing.JLabel skillTechnology;
    // End of variables declaration//GEN-END:variables

}
