/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SelectionTab.java
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

import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.ClickZoomWireListener;
import com.sun.electric.tool.user.ui.ToolBar;
import com.sun.electric.tool.user.ui.ToolBar.CursorMode;

import javax.swing.JPanel;

/**
 * Class to handle the "Selection" tab of the Preferences dialog.
 */
public class SelectionTab extends PreferencePanel
{
	/** Creates new form SelectionTab */
	public SelectionTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(selectionCancelMoveDelay);
	}

	/** return the panel to use for user preferences. */
	public JPanel getUserPreferencesPanel() { return selection; }

	/** return the name of this preferences tab. */
	public String getName() { return "Selection"; }

    private long cancelMoveDelayMillis;

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Selection tab.
	 */
	public void init()
	{
		selEasyCellInstances.setSelected(User.isEasySelectionOfCellInstances());
		selDraggingEnclosesEntireObject.setSelected(User.isDraggingMustEncloseObjects());
        cancelMoveDelayMillis = ClickZoomWireListener.theOne.getCancelMoveDelayMillis();
        selectionCancelMoveDelay.setText(String.valueOf(cancelMoveDelayMillis));
        useMouseOverHighlighting.setSelected(User.isMouseOverHighlightingEnabled());
        highlightConnectedObjects.setSelected(User.isHighlightConnectedObjects());
        selectInvisible.setSelected(User.isHighlightInvisibleObjects());
        routingMode.setSelected(User.isRoutingMode());
    }

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Selection tab.
	 */
	public void term()
	{
		boolean currBoolean = selEasyCellInstances.isSelected();
		if (currBoolean != User.isEasySelectionOfCellInstances())
			User.setEasySelectionOfCellInstances(currBoolean);

		currBoolean = selDraggingEnclosesEntireObject.isSelected();
		if (currBoolean != User.isDraggingMustEncloseObjects())
			User.setDraggingMustEncloseObjects(currBoolean);

        currBoolean = useMouseOverHighlighting.isSelected();
        if (currBoolean != User.isMouseOverHighlightingEnabled())
            User.setMouseOverHighlightingEnabled(currBoolean);

        currBoolean = highlightConnectedObjects.isSelected();
        if (currBoolean != User.isHighlightConnectedObjects())
            User.setHighlightConnectedObjects(currBoolean);

        currBoolean = selectInvisible.isSelected();
        if (currBoolean != User.isHighlightInvisibleObjects())
            User.setHighlightInvisibleObjects(currBoolean);

        currBoolean = routingMode.isSelected();
        if (currBoolean != User.isRoutingMode())
        {
            User.setRoutingMode(currBoolean);
        	if (currBoolean)
        	{
        		// enter routing mode
        		ToolBar.setCursorMode(CursorMode.ROUTING);
        	} else
        	{
        		// exit routing mode
				if (ToolBar.getCursorMode() == CursorMode.ROUTING)
					ToolBar.setCursorMode(CursorMode.CLICKZOOMWIRE);
        	}
        }

        long delay;
        try {
            Long num = Long.valueOf(selectionCancelMoveDelay.getText());
            delay = num.longValue();
        } catch (NumberFormatException e) {
            delay = cancelMoveDelayMillis;
        }
        if (delay != cancelMoveDelayMillis)
            ClickZoomWireListener.theOne.setCancelMoveDelayMillis(delay);
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		if (User.isFactoryEasySelectionOfCellInstances() != User.isEasySelectionOfCellInstances())
			User.setEasySelectionOfCellInstances(User.isFactoryEasySelectionOfCellInstances());
		if (User.isFactoryDraggingMustEncloseObjects() != User.isDraggingMustEncloseObjects())
			User.setDraggingMustEncloseObjects(User.isFactoryDraggingMustEncloseObjects());
		if (ClickZoomWireListener.getFactoryCancelMoveDelayMillis() != ClickZoomWireListener.theOne.getCancelMoveDelayMillis())
			ClickZoomWireListener.theOne.setCancelMoveDelayMillis(ClickZoomWireListener.getFactoryCancelMoveDelayMillis());
		if (User.isFactoryMouseOverHighlightingEnabled() != User.isMouseOverHighlightingEnabled())
			User.setMouseOverHighlightingEnabled(User.isFactoryMouseOverHighlightingEnabled());
		if (User.isFactoryHighlightConnectedObjects() != User.isHighlightConnectedObjects())
			User.setHighlightConnectedObjects(User.isFactoryHighlightConnectedObjects());
		if (User.isFactoryHighlightInvisibleObjects() != User.isHighlightInvisibleObjects())
			User.setHighlightInvisibleObjects(User.isFactoryHighlightInvisibleObjects());
		if (User.isFactoryRoutingMode() != User.isRoutingMode())
			User.setRoutingMode(User.isFactoryRoutingMode());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        selection = new javax.swing.JPanel();
        selEasyCellInstances = new javax.swing.JCheckBox();
        selDraggingEnclosesEntireObject = new javax.swing.JCheckBox();
        jLabel55 = new javax.swing.JLabel();
        selectionCancelMoveDelay = new javax.swing.JTextField();
        jLabel58 = new javax.swing.JLabel();
        useMouseOverHighlighting = new javax.swing.JCheckBox();
        highlightConnectedObjects = new javax.swing.JCheckBox();
        selectInvisible = new javax.swing.JCheckBox();
        routingMode = new javax.swing.JCheckBox();

        setTitle("Edit Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        selection.setLayout(new java.awt.GridBagLayout());

        selEasyCellInstances.setText("Easy selection of cell instances");
        selEasyCellInstances.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(selEasyCellInstances, gridBagConstraints);

        selDraggingEnclosesEntireObject.setText("Dragging must enclose entire object");
        selDraggingEnclosesEntireObject.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(selDraggingEnclosesEntireObject, gridBagConstraints);

        jLabel55.setText("Cancel move if move done within:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(jLabel55, gridBagConstraints);

        selectionCancelMoveDelay.setColumns(5);
        selectionCancelMoveDelay.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        selectionCancelMoveDelay.setToolTipText("Prevents accidental object movement when double-clicking");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(selectionCancelMoveDelay, gridBagConstraints);

        jLabel58.setText("ms");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(jLabel58, gridBagConstraints);

        useMouseOverHighlighting.setText("Enable Mouse-over highlighting");
        useMouseOverHighlighting.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(useMouseOverHighlighting, gridBagConstraints);

        highlightConnectedObjects.setText("Highlight Connected Objects");
        highlightConnectedObjects.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(highlightConnectedObjects, gridBagConstraints);

        selectInvisible.setText("Can select objects whose layers are invisible");
        selectInvisible.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(selectInvisible, gridBagConstraints);

        routingMode.setText("Routing mode (cannot change connectivity)");
        routingMode.setBorder(null);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        selection.add(routingMode, gridBagConstraints);

        getContentPane().add(selection, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox highlightConnectedObjects;
    private javax.swing.JLabel jLabel55;
    private javax.swing.JLabel jLabel58;
    private javax.swing.JCheckBox routingMode;
    private javax.swing.JCheckBox selDraggingEnclosesEntireObject;
    private javax.swing.JCheckBox selEasyCellInstances;
    private javax.swing.JCheckBox selectInvisible;
    private javax.swing.JPanel selection;
    private javax.swing.JTextField selectionCancelMoveDelay;
    private javax.swing.JCheckBox useMouseOverHighlighting;
    // End of variables declaration//GEN-END:variables

}
