/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ScaleTab.java
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

import com.sun.electric.technology.Technology;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.util.TextUtils;

import java.util.Iterator;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "Scale" tab of the Project Preferences dialog.
 */
public class ScaleTab extends PreferencePanel
{
	/** Creates new form ScaleTab */
	public ScaleTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(unitsScaleValue);
	}

	/** return the JPanel to use for the project preferences part of this tab. */
	public JPanel getProjectPreferencesPanel() { return scale; }

	/** return the name of this preferences tab. */
	public String getName() { return "Scale"; }

	private JList unitsTechnologyList;
	private DefaultListModel unitsTechnologyModel;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Scale tab.
	 */
	public void init()
	{
		// build the layers list
		unitsTechnologyModel = new DefaultListModel();
		unitsTechnologyList = new JList(unitsTechnologyModel);
		unitsTechnologyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		unitsList.setViewportView(unitsTechnologyList);
		unitsTechnologyList.clearSelection();
		unitsTechnologyList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { unitsClickTechnology(); }
		});
		unitsTechnologyModel.clear();
		int wantIndex = 0;
		int index = 0;
		for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
		{
			Technology tech = it.next();
			if (!tech.isScaleRelevant()) continue;
			double shownScale = getDouble(tech.getScaleSetting());
			unitsTechnologyModel.addElement(tech.getTechName() + " (scale=" + shownScale + " nanometers)");
			if (tech == Technology.getCurrent()) wantIndex = index;
			index++;
		}
		unitsTechnologyList.setSelectedIndex(wantIndex);
		unitsClickTechnology();

		unitsScaleValue.getDocument().addDocumentListener(new UnitsDocumentListener(this));
	}
	
	public void reset()
	{
		for(Iterator<Technology> it = Technology.getTechnologies(); it.hasNext(); )
		{
			Technology tech = it.next();
			if (!tech.isScaleRelevant()) continue;
			setDouble(tech.getScaleSetting(), ((Double)tech.getScaleSetting().getFactoryValue()).doubleValue());
		}
	}

	/**
	 * Class to handle special changes to changes to a Technology in the Scale panel.
	 */
	private static class UnitsDocumentListener implements DocumentListener
	{
		ScaleTab dialog;

		UnitsDocumentListener(ScaleTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.unitsNumbersChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.unitsNumbersChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.unitsNumbersChanged(); }
	}

	/**
	 * Method called when the user types a new scale factor into the edit fields.
	 */
	private void unitsNumbersChanged()
	{
		String str = (String)unitsTechnologyList.getSelectedValue();
		int spacePos = str.indexOf(" ");
		if (spacePos >= 0) str = str.substring(0, spacePos);
		Technology tech = Technology.findTechnology(str);
		if (tech == null) return;

		double shownScale = TextUtils.atof(unitsScaleValue.getText());
        if (shownScale <= 0) return;
		setDouble(tech.getScaleSetting(), shownScale);
		String newLine = tech.getTechName() + " (scale=" + shownScale + " nanometers)";
		int index = unitsTechnologyList.getSelectedIndex();
		unitsTechnologyModel.set(index, newLine);
		unitsAlternateScale.setText("nanometers (" + (shownScale/1000.0) + " microns)");
	}

	/**
	 * Method called when the user clicks on a layer name in the scrollable list.
	 */
	private void unitsClickTechnology()
	{
		String str = (String)unitsTechnologyList.getSelectedValue();
		int spacePos = str.indexOf(" ");
		if (spacePos >= 0) str = str.substring(0, spacePos);
		Technology tech = Technology.findTechnology(str);
		if (tech == null) return;
		unitsScaleValue.setText(getFormattedDouble(tech.getScaleSetting()));
		unitsAlternateScale.setText("nanometers (" + (getDouble(tech.getScaleSetting())/1000.0) + " microns)");
	}
    
	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        scale = new javax.swing.JPanel();
        unitsList = new javax.swing.JScrollPane();
        jLabel10 = new javax.swing.JLabel();
        unitsScaleValue = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        unitsAlternateScale = new javax.swing.JLabel();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("IO Options");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        scale.setLayout(new java.awt.GridBagLayout());

        unitsList.setMinimumSize(new java.awt.Dimension(150, 150));
        unitsList.setPreferredSize(new java.awt.Dimension(150, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        scale.add(unitsList, gridBagConstraints);

        jLabel10.setText("The technology scale converts grid units to real spacing on the chip:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        scale.add(jLabel10, gridBagConstraints);

        unitsScaleValue.setColumns(6);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        scale.add(unitsScaleValue, gridBagConstraints);

        jLabel11.setText("Technology scale:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        scale.add(jLabel11, gridBagConstraints);

        unitsAlternateScale.setText("nanometers");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        scale.add(unitsAlternateScale, gridBagConstraints);

        getContentPane().add(scale, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JPanel scale;
    private javax.swing.JLabel unitsAlternateScale;
    private javax.swing.JScrollPane unitsList;
    private javax.swing.JTextField unitsScaleValue;
    // End of variables declaration//GEN-END:variables
}
