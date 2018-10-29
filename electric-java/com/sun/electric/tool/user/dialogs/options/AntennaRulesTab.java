/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AntennaRulesTab.java
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

import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.erc.ERCAntenna;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.util.TextUtils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
 * Class to handle the "Antenna Rules" tab of the Preferences dialog.
 */
public class AntennaRulesTab extends PreferencePanel
{
	/** Creates new form AntennaRulesTab */
	public AntennaRulesTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(antMaxRatio);
	}

	/** return the panel to use for the user preferences. */
	public JPanel getUserPreferencesPanel() { return antennaRules; }

	/** return the name of this preferences tab. */
	public String getName() { return "Antenna Rules"; }

	private JList antennaArcList;
	private DefaultListModel antennaArcListModel;
    private ERCAntenna.AntennaPreferences antennaOptions;
	private boolean antennaRatioChanging = false;
	private boolean empty;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Antenna Rules tab.
	 */
    @Override
	public void init()
	{
		antennaArcListModel = new DefaultListModel();
		antennaArcList = new JList(antennaArcListModel);
		antennaArcList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		antArcList.setViewportView(antennaArcList);
		antennaArcList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { antennaArcListClick(); }
		});
		antMaxRatio.getDocument().addDocumentListener(new AntennaRatioDocumentListener(this));

        antennaOptions = new ERCAntenna.AntennaPreferences(false, getTechPool());
		for(Technology tech: getTechPool().values())
			technologySelection.addItem(tech.getTechName());
		technologySelection.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { newTechSelected(); }
		});
		technologySelection.setSelectedItem(Technology.getCurrent().getTechName());
	}

	private void newTechSelected()
	{
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;
		boolean foundSome = false;
		empty = true;
		antennaArcListModel.clear();
		for(Iterator<ArcProto> it = tech.getArcs(); it.hasNext(); )
		{
			ArcProto ap = it.next();
            ArcProto.Function fun = ap.getFunction();
            if (!fun.isMetal() && fun != ArcProto.Function.POLY1) continue;
            double antennaRatio = antennaOptions.getAntennaRatio(ap);
			antennaArcListModel.addElement(ap.getName() + " (" + antennaRatio + ")");
			foundSome = true;
		}
		if (foundSome)
		{
			empty = !foundSome;
			antennaArcList.setSelectedIndex(0);
		}
	}

	private void antennaArcListClick()
	{
		if (empty) return;
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;
		String arcName = (String)antennaArcList.getSelectedValue();
		int spacePos = arcName.indexOf(' ');
		if (spacePos >= 0) arcName = arcName.substring(0, spacePos);
		ArcProto ap = tech.findArcProto(arcName);
		if (ap != null)
		{
            double antennaRatio = antennaOptions.getAntennaRatio(ap);
			antennaRatioChanging = true;
			antMaxRatio.setText(TextUtils.formatDouble(antennaRatio));
			antennaRatioChanging = false;
		}
	}

	private void antennaValueChanged()
	{
		if (empty) return;
		if (antennaRatioChanging) return;
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;
		String arcName = (String)antennaArcList.getSelectedValue();
		int spacePos = arcName.indexOf(' ');
		if (spacePos >= 0) arcName = arcName.substring(0, spacePos);
		ArcProto ap = tech.findArcProto(arcName);
		if (ap == null) return;
		double ratio = TextUtils.atof(antMaxRatio.getText());
        antennaOptions.antennaRatio.put(ap.getId(), new Double(ratio));

		int lineNo = antennaArcList.getSelectedIndex();
		antennaArcListModel.setElementAt(ap.getName() + " (" + ratio + ")", lineNo);
	}

	/**
	 * Class to handle changes to the antenna ratio field.
	 */
	private static class AntennaRatioDocumentListener implements DocumentListener
	{
		AntennaRulesTab dialog;

		AntennaRatioDocumentListener(AntennaRulesTab dialog)
		{
			this.dialog = dialog;
		}

		public void changedUpdate(DocumentEvent e) { dialog.antennaValueChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.antennaValueChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.antennaValueChanged(); }
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Antenna Rules tab.
	 */
    @Override
	public void term()
	{
        putPrefs(antennaOptions);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
    @Override
	public void reset()
	{
        putPrefs(new ERCAntenna.AntennaPreferences(true, getTechPool()));
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        antennaRules = new javax.swing.JPanel();
        jLabel66 = new javax.swing.JLabel();
        antArcList = new javax.swing.JScrollPane();
        jLabel68 = new javax.swing.JLabel();
        antMaxRatio = new javax.swing.JTextField();
        technologySelection = new javax.swing.JComboBox();

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

        antennaRules.setLayout(new java.awt.GridBagLayout());

        jLabel66.setText("Arcs in technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        antennaRules.add(jLabel66, gridBagConstraints);

        antArcList.setPreferredSize(new java.awt.Dimension(300, 200));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        antennaRules.add(antArcList, gridBagConstraints);

        jLabel68.setText("Maximum antenna ratio:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        antennaRules.add(jLabel68, gridBagConstraints);

        antMaxRatio.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        antennaRules.add(antMaxRatio, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        antennaRules.add(technologySelection, gridBagConstraints);

        getContentPane().add(antennaRules, new java.awt.GridBagConstraints());

        pack();
    }
    // </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane antArcList;
    private javax.swing.JTextField antMaxRatio;
    private javax.swing.JPanel antennaRules;
    private javax.swing.JLabel jLabel66;
    private javax.swing.JLabel jLabel68;
    private javax.swing.JComboBox technologySelection;
    // End of variables declaration//GEN-END:variables

}
