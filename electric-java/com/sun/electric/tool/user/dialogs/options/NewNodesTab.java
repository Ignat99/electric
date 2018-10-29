/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NewNodesTab.java
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

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.util.math.DBMath;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;

import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Class to handle the "New Nodes" tab of the Preferences dialog.
 */
public class NewNodesTab extends PreferencePanel
{
	/** Creates new form NewNodesTab */
	public NewNodesTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(nodePrimitiveXSize);
	    EDialog.makeTextFieldSelectAllOnTab(nodePrimitiveYSize);
	}

	/** return the panel to use for user preferences. */
    @Override
	public JPanel getUserPreferencesPanel() { return newNode; }

	/** return the name of this preferences tab. */
    @Override
	public String getName() { return "Nodes"; }

	private boolean newNodesDataChanging = false;
	private Technology selectedTech;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the New Nodes tab.
	 */
    @Override
	public void init()
	{
		// gather information about the PrimitiveNodes in the current Technology
		for(Iterator<Technology> tIt = Technology.getTechnologies(); tIt.hasNext(); )
		{
			Technology tech = tIt.next();
			technologySelection.addItem(tech.getTechName());
		}
		technologySelection.setSelectedItem(Technology.getCurrent().getTechName());
		selectedTech = null;
		newNodesPrimPopupChanged();

		// set checkboxes for "Cells" area
        EditingPreferences ep = getEditingPreferences();
		nodeCheckCellDates.setSelected(User.isCheckCellDates());
		nodeSwitchTechnology.setSelected(User.isAutoTechnologySwitch());
		nodePlaceCellCenter.setSelected(ep.isPlaceCellCenter());
		nodeReconstructArcsExports.setSelected(User.isReconstructArcsAndExportsToDeletedCells());
		nodeConvertSchAndLay.setSelected(User.isConvertSchematicLayoutWhenPasting());
		nodePromptForIndex.setSelected(User.isPromptForIndexWhenDescending());

		// set checkboxes for "all nodes" area
		nodeDisallowModificationComplexNodes.setSelected(User.isDisallowModificationComplexNodes());
		nodeDisallowModificationLockedPrims.setSelected(User.isDisallowModificationLockedPrims());
		nodeMoveAfterDuplicate.setSelected(User.isMoveAfterDuplicate());
		nodeDupArrayCopyExports1.setSelected(User.isDuplicateInPlace());
		nodeDupArrayCopyExports.setSelected(User.isDupCopiesExports());
		nodeIncrementRightmostIndex.setSelected(User.isIncrementRightmostIndex());
		nodeExtractCopyExports.setSelected(User.isExtractCopiesExports());

		// setup listeners to react to any changes to a primitive size
		technologySelection.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { newNodesPrimPopupChanged(); }
		});
		nodePrimitive.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { newNodesPrimPopupChanged(); }
		});
		nodePrimitiveXSize.getDocument().addDocumentListener(new NewNodeDocumentListener(this));
		nodePrimitiveYSize.getDocument().addDocumentListener(new NewNodeDocumentListener(this));
	}

	/**
	 * Method called when the primitive node popup is changed.
	 */
	private void newNodesPrimPopupChanged()
	{
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;
		if (tech != selectedTech)
		{
			// reload the primitives
			selectedTech = tech;
			nodePrimitive.removeAllItems();
			for(Iterator<PrimitiveNode> it = tech.getNodes(); it.hasNext(); )
			{
				PrimitiveNode np = it.next();
				nodePrimitive.addItem(np.getName());
			}
		}
		String primName = (String)nodePrimitive.getSelectedItem();
		PrimitiveNode np = tech.findNodeProto(primName);
        ImmutableNodeInst n = np.getDefaultInst(getEditingPreferences());
		newNodesDataChanging = true;
        ERectangle base = np.getBaseRectangle();
        double wid = DBMath.gridToLambda(base.getGridWidth() + n.size.getGridX());
        double hei = DBMath.gridToLambda(base.getGridHeight() + n.size.getGridY());
		nodePrimitiveXSize.setText(TextUtils.formatDistance(wid, tech));
		nodePrimitiveYSize.setText(TextUtils.formatDistance(hei, tech));
		newNodesDataChanging = false;
	}

	/**
	 * Class to handle special changes to per-primitive node options.
	 */
	private static class NewNodeDocumentListener implements DocumentListener
	{
		NewNodesTab dialog;

		NewNodeDocumentListener(NewNodesTab dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.newNodesPrimDataChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.newNodesPrimDataChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.newNodesPrimDataChanged(); }
	}

	/**
	 * Method called when any of the primitive data (in the top part) changes.
	 * Caches all values for the selected primitive node.
	 */
	private void newNodesPrimDataChanged()
	{
		if (newNodesDataChanging) return;
		String techName = (String)technologySelection.getSelectedItem();
		Technology tech = Technology.findTechnology(techName);
		if (tech == null) return;
		String primName = (String)nodePrimitive.getSelectedItem();
		PrimitiveNode np = tech.findNodeProto(primName);
        ERectangle base = np.getBaseRectangle();
        double wid = TextUtils.atofDistance(nodePrimitiveXSize.getText(), tech);
        double hei = TextUtils.atofDistance(nodePrimitiveYSize.getText(), tech);
		EPoint size = EPoint.fromGrid(DBMath.lambdaToSizeGrid(wid - base.getWidth()), DBMath.lambdaToSizeGrid(hei - base.getHeight()));
        setEditingPreferences(getEditingPreferences().withNodeSize(np.getId(), size));
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the New Nodes tab.
	 */
    @Override
	public void term()
	{
		boolean currBoolean = nodeCheckCellDates.isSelected();
		if (currBoolean != User.isCheckCellDates())
			User.setCheckCellDates(currBoolean);

		currBoolean = nodeSwitchTechnology.isSelected();
		if (currBoolean != User.isAutoTechnologySwitch())
			User.setAutoTechnologySwitch(currBoolean);

		currBoolean = nodeReconstructArcsExports.isSelected();
		if (currBoolean != User.isReconstructArcsAndExportsToDeletedCells())
			User.setReconstructArcsAndExportsToDeletedCells(currBoolean);

		currBoolean = nodeConvertSchAndLay.isSelected();
		if (currBoolean != User.isConvertSchematicLayoutWhenPasting())
			User.setConvertSchematicLayoutWhenPasting(currBoolean);

		currBoolean = nodePromptForIndex.isSelected();
		if (currBoolean != User.isPromptForIndexWhenDescending())
			User.setPromptForIndexWhenDescending(currBoolean);

		currBoolean = nodeDisallowModificationComplexNodes.isSelected();
		if (currBoolean != User.isDisallowModificationComplexNodes())
			User.setDisallowModificationComplexNodes(currBoolean);

		currBoolean = nodeDisallowModificationLockedPrims.isSelected();
		if (currBoolean != User.isDisallowModificationLockedPrims())
			User.setDisallowModificationLockedPrims(currBoolean);

		currBoolean = nodeMoveAfterDuplicate.isSelected();
		if (currBoolean != User.isMoveAfterDuplicate())
			User.setMoveAfterDuplicate(currBoolean);

		currBoolean = nodeDupArrayCopyExports1.isSelected();
		if (currBoolean != User.isDuplicateInPlace())
			User.setDuplicateInPlace(currBoolean);

		currBoolean = nodeDupArrayCopyExports.isSelected();
		if (currBoolean != User.isDupCopiesExports())
			User.setDupCopiesExports(currBoolean);

		currBoolean = nodeIncrementRightmostIndex.isSelected();
		if (currBoolean != User.isIncrementRightmostIndex())
			User.setIncrementRightmostIndex(currBoolean);

		currBoolean = nodeExtractCopyExports.isSelected();
		if (currBoolean != User.isExtractCopiesExports())
			User.setExtractCopiesExports(currBoolean);

        EditingPreferences ep = getEditingPreferences();
        ep = ep.withPlaceCellCenter(nodePlaceCellCenter.isSelected());
        setEditingPreferences(ep);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
    @Override
	public void reset()
	{
        setEditingPreferences(getEditingPreferences().withNodesReset());

		if (User.isFactoryCheckCellDates() != User.isCheckCellDates())
			User.setCheckCellDates(User.isFactoryCheckCellDates());
		if (User.isFactoryAutoTechnologySwitch() != User.isAutoTechnologySwitch())
			User.setAutoTechnologySwitch(User.isFactoryAutoTechnologySwitch());
		if (User.isFactoryReconstructArcsAndExportsToDeletedCells() != User.isReconstructArcsAndExportsToDeletedCells())
			User.setReconstructArcsAndExportsToDeletedCells(User.isFactoryReconstructArcsAndExportsToDeletedCells());
		if (User.isFactoryConvertSchematicLayoutWhenPasting() != User.isConvertSchematicLayoutWhenPasting())
			User.setConvertSchematicLayoutWhenPasting(User.isFactoryConvertSchematicLayoutWhenPasting());
		if (User.isFactoryPromptForIndexWhenDescending() != User.isPromptForIndexWhenDescending())
			User.setPromptForIndexWhenDescending(User.isFactoryPromptForIndexWhenDescending());

		if (User.isFactoryDisallowModificationComplexNodes() != User.isDisallowModificationComplexNodes())
			User.setDisallowModificationComplexNodes(User.isFactoryDisallowModificationComplexNodes());
		if (User.isFactoryDisallowModificationLockedPrims() != User.isDisallowModificationLockedPrims())
			User.setDisallowModificationLockedPrims(User.isFactoryDisallowModificationLockedPrims());
		if (User.isFactoryMoveAfterDuplicate() != User.isMoveAfterDuplicate())
			User.setMoveAfterDuplicate(User.isFactoryMoveAfterDuplicate());
		if (User.isFactoryDuplicateInPlace() != User.isDuplicateInPlace())
			User.setDuplicateInPlace(User.isFactoryDuplicateInPlace());
		if (User.isFactoryDupCopiesExports() != User.isDupCopiesExports())
			User.setDupCopiesExports(User.isFactoryDupCopiesExports());
		if (User.isFactoryIncrementRightmostIndex() != User.isIncrementRightmostIndex())
			User.setIncrementRightmostIndex(User.isFactoryIncrementRightmostIndex());
		if (User.isFactoryExtractCopiesExports() != User.isExtractCopiesExports())
			User.setExtractCopiesExports(User.isFactoryExtractCopiesExports());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        newNode = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        nodePrimitive = new javax.swing.JComboBox();
        nodePrimitiveXSize = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        nodePrimitiveYSize = new javax.swing.JTextField();
        technologySelection = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        nodeCheckCellDates = new javax.swing.JCheckBox();
        nodeSwitchTechnology = new javax.swing.JCheckBox();
        nodePlaceCellCenter = new javax.swing.JCheckBox();
        nodeReconstructArcsExports = new javax.swing.JCheckBox();
        nodePromptForIndex = new javax.swing.JCheckBox();
        nodeConvertSchAndLay = new javax.swing.JCheckBox();
        jPanel4 = new javax.swing.JPanel();
        nodeDisallowModificationLockedPrims = new javax.swing.JCheckBox();
        nodeMoveAfterDuplicate = new javax.swing.JCheckBox();
        nodeDupArrayCopyExports = new javax.swing.JCheckBox();
        nodeExtractCopyExports = new javax.swing.JCheckBox();
        nodeDisallowModificationComplexNodes = new javax.swing.JCheckBox();
        nodeDupArrayCopyExports1 = new javax.swing.JCheckBox();
        nodeIncrementRightmostIndex = new javax.swing.JCheckBox();

        setTitle("Edit Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        newNode.setLayout(new java.awt.GridBagLayout());

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("For New Primitive Nodes"));
        jPanel2.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Primitive:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(nodePrimitive, gridBagConstraints);

        nodePrimitiveXSize.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(nodePrimitiveXSize, gridBagConstraints);

        jLabel2.setText("Default X size:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel2, gridBagConstraints);

        jLabel3.setText("Default Y size:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel3, gridBagConstraints);

        nodePrimitiveYSize.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(nodePrimitiveYSize, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(technologySelection, gridBagConstraints);

        jLabel4.setText("Technology:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel2.add(jLabel4, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        newNode.add(jPanel2, gridBagConstraints);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("For Cells and Cell Instances"));
        jPanel3.setLayout(new java.awt.GridBagLayout());

        nodeCheckCellDates.setText("Check cell dates during editing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        jPanel3.add(nodeCheckCellDates, gridBagConstraints);

        nodeSwitchTechnology.setText("Switch technology to match current cell");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel3.add(nodeSwitchTechnology, gridBagConstraints);

        nodePlaceCellCenter.setText("Place Cell-Center in new cells");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel3.add(nodePlaceCellCenter, gridBagConstraints);

        nodeReconstructArcsExports.setText("Reconstruct arcs and exports when deleting instances");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel3.add(nodeReconstructArcsExports, gridBagConstraints);

        nodePromptForIndex.setText("Always prompt for index when descending into array nodes");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel3.add(nodePromptForIndex, gridBagConstraints);

        nodeConvertSchAndLay.setText("Convert between schematic and layout when pasting");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel3.add(nodeConvertSchAndLay, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        newNode.add(jPanel3, gridBagConstraints);

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("For All Nodes"));
        jPanel4.setLayout(new java.awt.GridBagLayout());

        nodeDisallowModificationLockedPrims.setText("Disallow modification of locked primitives");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel4.add(nodeDisallowModificationLockedPrims, gridBagConstraints);

        nodeMoveAfterDuplicate.setText("Move after Duplicate");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel4.add(nodeMoveAfterDuplicate, gridBagConstraints);

        nodeDupArrayCopyExports.setText("Duplicate/Array/Paste copies exports");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 4);
        jPanel4.add(nodeDupArrayCopyExports, gridBagConstraints);

        nodeExtractCopyExports.setText("Extract copies exports");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        jPanel4.add(nodeExtractCopyExports, gridBagConstraints);

        nodeDisallowModificationComplexNodes.setText("Disallow modification of complex nodes");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        jPanel4.add(nodeDisallowModificationComplexNodes, gridBagConstraints);

        nodeDupArrayCopyExports1.setText("Duplicate In Place");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        jPanel4.add(nodeDupArrayCopyExports1, gridBagConstraints);

        nodeIncrementRightmostIndex.setText("Increment rightmost array index");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 2, 4);
        jPanel4.add(nodeIncrementRightmostIndex, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        newNode.add(jPanel4, gridBagConstraints);

        getContentPane().add(newNode, new java.awt.GridBagConstraints());

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
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel newNode;
    private javax.swing.JCheckBox nodeCheckCellDates;
    private javax.swing.JCheckBox nodeConvertSchAndLay;
    private javax.swing.JCheckBox nodeDisallowModificationComplexNodes;
    private javax.swing.JCheckBox nodeDisallowModificationLockedPrims;
    private javax.swing.JCheckBox nodeDupArrayCopyExports;
    private javax.swing.JCheckBox nodeDupArrayCopyExports1;
    private javax.swing.JCheckBox nodeExtractCopyExports;
    private javax.swing.JCheckBox nodeIncrementRightmostIndex;
    private javax.swing.JCheckBox nodeMoveAfterDuplicate;
    private javax.swing.JCheckBox nodePlaceCellCenter;
    private javax.swing.JComboBox nodePrimitive;
    private javax.swing.JTextField nodePrimitiveXSize;
    private javax.swing.JTextField nodePrimitiveYSize;
    private javax.swing.JCheckBox nodePromptForIndex;
    private javax.swing.JCheckBox nodeReconstructArcsExports;
    private javax.swing.JCheckBox nodeSwitchTechnology;
    private javax.swing.JComboBox technologySelection;
    // End of variables declaration//GEN-END:variables
}
