/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NewArcsTab.java
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
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.extract.LayerCoverageTool;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.TopLevel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "New Arcs" tab of the Preferences dialog.
 */
public class CoverageTab extends PreferencePanel
{
	/** Creates new form CoverageTab */
	public CoverageTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(layerAreaField);
	    EDialog.makeTextFieldSelectAllOnTab(widthField);
	    EDialog.makeTextFieldSelectAllOnTab(heightField);
	    EDialog.makeTextFieldSelectAllOnTab(deltaXField);
	    EDialog.makeTextFieldSelectAllOnTab(deltaYField);
	}

	/** return the panel to use for the user preferences. */
    @Override
	public JPanel getUserPreferencesPanel() { return layerCoverage; }

	/** return the name of this preferences tab. */
    @Override
	public String getName() { return "Coverage"; }

	private boolean layerDataChanging = false;
    private LayerCoverageTool.LayerCoveragePreferences lcp;
	private DefaultListModel layerListModel;
	private JList layerJList;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the New Arcs tab.
	 */
    @Override
	public void init()
	{
		lcp = new LayerCoverageTool.LayerCoveragePreferences(false);
		layerListModel = new DefaultListModel();
		layerJList = new JList(layerListModel);
		layerJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		listScrollList.setViewportView(layerJList);

		for(Iterator<Technology> tIt = Technology.getTechnologies(); tIt.hasNext(); )
		{
			Technology tech = tIt.next();
			technologySelection.addItem(tech.getTechName());
		}
		layerDataChanging = false;
		layerAreaField.getDocument().addDocumentListener(new CoverageDocumentListener(this));

		technologySelection.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { newTechSelected(); }
		});
		layerJList.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { layerValueChanged(false); }
		});
//		layerJList.addMouseListener(new MouseAdapter()
//		{
//			public void mouseClicked(MouseEvent evt) { layerValueChanged(false); }
//		});
		technologySelection.setSelectedItem(Technology.getCurrent().getTechName());
	}

	private void newTechSelected()
	{
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;
		layerDataChanging = true;
		layerListModel.clear();
		List<Layer> list = tech.getLayersSortedByUserPreference();
		for (Layer layer : list)
		{
			layerListModel.addElement(getLineString(layer, lcp.getAreaCoverage(layer)));
		}
		layerJList.setSelectedIndex(0);
        double techScale = tech.getScale();
		widthField.setText(TextUtils.formatDistance(lcp.widthInMicrons/techScale));
		heightField.setText(TextUtils.formatDistance(lcp.heightInMicrons/techScale));
		deltaXField.setText(TextUtils.formatDistance(lcp.deltaXInMicrons/techScale));
		deltaYField.setText(TextUtils.formatDistance(lcp.deltaYInMicrons/techScale));
		layerDataChanging = false;

		layerValueChanged(false);
	}

    /**
     * Method called when other preferences tab changed the layers sorting algorithm
     */
    @Override
    public void refresh()
    {
    	newTechSelected();
    }

	private static String getLineString(Layer layer, double value)
	{
		return layer.getName() + " (" + value + ")";
	}

	/**
	 * Class to handle changes to the area.
	 */
	private static class CoverageDocumentListener implements DocumentListener
	{
		CoverageTab dialog;

		CoverageDocumentListener(CoverageTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.layerValueChanged(true); }
		public void insertUpdate(DocumentEvent e) { dialog.layerValueChanged(true);}
		public void removeUpdate(DocumentEvent e) { dialog.layerValueChanged(true); }
	}

	private static class SetOriginalValue implements Runnable
	{
		javax.swing.JTextField field;
		double origValue;

		public SetOriginalValue(JTextField field, double value)
		{
			this.field = field;
			this.origValue = value;
		}
		public void run()
		{
			field.setText(TextUtils.formatDouble(origValue));
		}
	}

	/**
	 * Method called when the layer popup is changed. Return false
	 * if there was an error and need to reset back to original value
	 * @param set true if the value has been changed
	 */
	private void layerValueChanged(boolean set)
	{
		if (layerDataChanging) return;
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;

		String primName = (String)layerJList.getSelectedValue();
		int spacePos = primName.indexOf(' ');
		if (spacePos >= 0) primName = primName.substring(0, spacePos);
		Layer layer = tech.findLayer(primName);
		double origValue = lcp.getAreaCoverage(layer);
		if (set)
		{
			double val = 0;
			boolean foundError = false;
			String text = layerAreaField.getText();

			try
			{
				if (text.equals("")) return;  // ignore this case
				val = Double.parseDouble(text);
				foundError = (val < 0 || val > 100);
			}
			catch (NumberFormatException e)
			{
				foundError = true;
			}
			if (foundError)
			{
				// set back to original value
				JOptionPane.showMessageDialog(TopLevel.getCurrentJFrame(),
					text + " is out of range area value xfor layer '" + layer.getName() + "' (valid range 0 -> 100).");
				SwingUtilities.invokeLater(new SetOriginalValue(layerAreaField, origValue));
				return;
			}
            lcp.setAreaCoverageInfo(layer, val);
			int lineNo = layerJList.getSelectedIndex();
			layerListModel.setElementAt(getLineString(layer, val), lineNo);
		}
		else
			layerAreaField.setText(TextUtils.formatDouble(origValue));
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the New Arcs tab.
	 */
    @Override
	public void term()
	{
		// Default values are 50mm x 50 mm
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech != null)
		{
            double techScale = tech.getScale();
            lcp.widthInMicrons = TextUtils.atofDistance(widthField.getText())*techScale;
            lcp.heightInMicrons = TextUtils.atofDistance(heightField.getText())*techScale;
            lcp.deltaXInMicrons = TextUtils.atofDistance(deltaXField.getText())*techScale;
            lcp.deltaYInMicrons = TextUtils.atofDistance(deltaYField.getText())*techScale;
		}

        putPrefs(lcp);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
    @Override
	public void reset()
	{
        if (lcp == null)
            return; // nothing to reset
        lcp.reset();
        putPrefs(lcp);
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

        layerCoverage = new javax.swing.JPanel();
        layerPanel = new javax.swing.JPanel();
        layerAreaLabel = new javax.swing.JLabel();
        layerAreaField = new javax.swing.JTextField();
        listScrollList = new javax.swing.JScrollPane();
        boundingSelection = new javax.swing.JPanel();
        widthLabel = new javax.swing.JLabel();
        widthField = new javax.swing.JTextField();
        heightLabel = new javax.swing.JLabel();
        heightField = new javax.swing.JTextField();
        deltaXLabel = new javax.swing.JLabel();
        deltaXField = new javax.swing.JTextField();
        deltaYLabel = new javax.swing.JLabel();
        deltaYField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        technologySelection = new javax.swing.JComboBox();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Edit Options");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });

        layerCoverage.setLayout(new java.awt.GridBagLayout());

        layerPanel.setLayout(new java.awt.GridBagLayout());

        layerPanel.setBorder(new javax.swing.border.TitledBorder("Layers:"));
        layerPanel.setDoubleBuffered(false);
        layerAreaLabel.setText("Coverage Area (%):");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(layerAreaLabel, gridBagConstraints);

        layerAreaField.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerPanel.add(layerAreaField, gridBagConstraints);

        listScrollList.setPreferredSize(new java.awt.Dimension(200, 300));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        layerPanel.add(listScrollList, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        layerCoverage.add(layerPanel, gridBagConstraints);

        boundingSelection.setLayout(new java.awt.GridBagLayout());

        boundingSelection.setBorder(new javax.swing.border.TitledBorder("Bounding Selection"));
        boundingSelection.setDoubleBuffered(false);
        widthLabel.setText("Width:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(widthLabel, gridBagConstraints);

        widthField.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(widthField, gridBagConstraints);

        heightLabel.setText("Height:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(heightLabel, gridBagConstraints);

        heightField.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(heightField, gridBagConstraints);

        deltaXLabel.setText("DeltaX:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(deltaXLabel, gridBagConstraints);

        deltaXField.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(deltaXField, gridBagConstraints);

        deltaYLabel.setText("DeltaY:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(deltaYLabel, gridBagConstraints);

        deltaYField.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        boundingSelection.add(deltaYField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 4, 0);
        layerCoverage.add(boundingSelection, gridBagConstraints);

        jLabel1.setText("Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerCoverage.add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        layerCoverage.add(technologySelection, gridBagConstraints);

        getContentPane().add(layerCoverage, new java.awt.GridBagConstraints());

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
    private javax.swing.JPanel boundingSelection;
    private javax.swing.JTextField deltaXField;
    private javax.swing.JLabel deltaXLabel;
    private javax.swing.JTextField deltaYField;
    private javax.swing.JLabel deltaYLabel;
    private javax.swing.JTextField heightField;
    private javax.swing.JLabel heightLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JTextField layerAreaField;
    private javax.swing.JLabel layerAreaLabel;
    private javax.swing.JPanel layerCoverage;
    private javax.swing.JPanel layerPanel;
    private javax.swing.JScrollPane listScrollList;
    private javax.swing.JComboBox technologySelection;
    private javax.swing.JTextField widthField;
    private javax.swing.JLabel widthLabel;
    // End of variables declaration//GEN-END:variables

}
