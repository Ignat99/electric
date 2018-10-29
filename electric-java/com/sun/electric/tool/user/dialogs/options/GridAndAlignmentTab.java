/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GridAndAlignmentTab.java
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
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.dialogs.PreferencesFrame;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.ToolBar;
import com.sun.electric.util.math.EDimension;


import javax.swing.AbstractButton;
import javax.swing.JPanel;

/**
 * Class to handle the "Grid And Alignment" tab of the Preferences dialog.
 */
public class GridAndAlignmentTab extends PreferencePanel
{
	/** Creates new form GridAndAlignment */
	public GridAndAlignmentTab(PreferencesFrame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(gridCurrentHoriz);
	    EDialog.makeTextFieldSelectAllOnTab(gridCurrentVert);
	    EDialog.makeTextFieldSelectAllOnTab(gridNewHoriz);
	    EDialog.makeTextFieldSelectAllOnTab(gridNewVert);
	    EDialog.makeTextFieldSelectAllOnTab(gridBoldHoriz);
	    EDialog.makeTextFieldSelectAllOnTab(gridBoldVert);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeX1);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeX2);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeX3);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeX4);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeX5);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeY1);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeY2);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeY3);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeY4);
	    EDialog.makeTextFieldSelectAllOnTab(gridSizeY5);
	}

	/** return the panel to use for user preferences. */
	public JPanel getUserPreferencesPanel() { return grid; }

	/** return the name of this preferences tab. */
	public String getName() { return "Grid"; }

	/**
	 * Method called at the start of the dialog.
	 * Caches current values and displays them in the Grid tab.
	 */
	public void init()
	{
		EditWindow wnd = EditWindow.getCurrent();
		if (wnd == null)
		{
			gridCurrentHoriz.setEditable(false);
			gridCurrentHoriz.setText("");
			gridCurrentVert.setEditable(false);
			gridCurrentVert.setText("");
		} else
		{
			gridCurrentHoriz.setEditable(true);
			gridCurrentHoriz.setText(TextUtils.formatDistance(wnd.getGridXSpacing()));
			gridCurrentVert.setEditable(true);
			gridCurrentVert.setText(TextUtils.formatDistance(wnd.getGridYSpacing()));
		}

		gridNewHoriz.setText(TextUtils.formatDistance(User.getDefGridXSpacing()));
		gridNewVert.setText(TextUtils.formatDistance(User.getDefGridYSpacing()));
		gridBoldHoriz.setText(Integer.toString(User.getDefGridXBoldFrequency()));
		gridBoldVert.setText(Integer.toString(User.getDefGridYBoldFrequency()));
		gridShowAxes.setSelected(User.isGridAxesShown());
		alignMeasurementCursor.setSelected(User.isGridAlignMeasurementCursor());

        EditingPreferences ep = getEditingPreferences();
		EDimension[] values = ep.getAlignmentToGridVector();
        gridSizeX1.setText(TextUtils.formatDistance(values[0].getWidth()));
        gridSizeX2.setText(TextUtils.formatDistance(values[1].getWidth()));
        gridSizeX3.setText(TextUtils.formatDistance(values[2].getWidth()));
        gridSizeX4.setText(TextUtils.formatDistance(values[3].getWidth()));
        gridSizeX5.setText(TextUtils.formatDistance(values[4].getWidth()));
        gridSizeY1.setText(TextUtils.formatDistance(values[0].getHeight()));
        gridSizeY2.setText(TextUtils.formatDistance(values[1].getHeight()));
        gridSizeY3.setText(TextUtils.formatDistance(values[2].getHeight()));
        gridSizeY4.setText(TextUtils.formatDistance(values[3].getHeight()));
        gridSizeY5.setText(TextUtils.formatDistance(values[4].getHeight()));
        AbstractButton[] list = {size1Button, size2Button, size3Button, size4Button, size5Button};
        int selInd = ep.getAlignmentToGridIndex();
        list[selInd].setSelected(true);
	}

	/**
	 * Method called when the "OK" panel is hit.
	 * Updates any changed fields in the Grid tab.
	 */
	public void term()
	{
		EditWindow wnd = EditWindow.getCurrent();
        double currDouble;

		boolean redraw = false;
		if (wnd != null)
		{
			currDouble = TextUtils.atofDistance(gridCurrentHoriz.getText());
			if (currDouble != wnd.getGridXSpacing())
			{
				wnd.setGridXSpacing(currDouble);
				redraw = true;
			}

			currDouble = TextUtils.atofDistance(gridCurrentVert.getText());
			if (currDouble != wnd.getGridYSpacing())
			{
				wnd.setGridYSpacing(currDouble);
				redraw = true;
			}
		}

		currDouble = TextUtils.atofDistance(gridNewHoriz.getText());
		if (currDouble != User.getDefGridXSpacing())
			User.setDefGridXSpacing(currDouble);

		currDouble = TextUtils.atofDistance(gridNewVert.getText());
		if (currDouble != User.getDefGridYSpacing())
			User.setDefGridYSpacing(currDouble);

		int currInt = TextUtils.atoi(gridBoldHoriz.getText());
		if (currInt != User.getDefGridXBoldFrequency())
			User.setDefGridXBoldFrequency(currInt);

		currInt = TextUtils.atoi(gridBoldVert.getText());
		if (currInt != User.getDefGridYBoldFrequency())
			User.setDefGridYBoldFrequency(currInt);

		boolean curBoolean = gridShowAxes.isSelected();
		if (curBoolean != User.isGridAxesShown())
		{
			User.setGridAxesShown(curBoolean);
			redraw = true;
		}

		curBoolean = alignMeasurementCursor.isSelected();
		if (curBoolean != User.isGridAlignMeasurementCursor())
		{
			User.setGridAlignMeasurementCursor(curBoolean);
			redraw = true;
		}

        EDimension [] newValues = new EDimension[5];
        newValues[0] = new EDimension(TextUtils.atofDistance(gridSizeX1.getText()), TextUtils.atofDistance(gridSizeY1.getText()));
        newValues[1] = new EDimension(TextUtils.atofDistance(gridSizeX2.getText()), TextUtils.atofDistance(gridSizeY2.getText()));
        newValues[2] = new EDimension(TextUtils.atofDistance(gridSizeX3.getText()), TextUtils.atofDistance(gridSizeY3.getText()));
        newValues[3] = new EDimension(TextUtils.atofDistance(gridSizeX4.getText()), TextUtils.atofDistance(gridSizeY4.getText()));
        newValues[4] = new EDimension(TextUtils.atofDistance(gridSizeX5.getText()), TextUtils.atofDistance(gridSizeY5.getText()));
        int pos = 0;
        if (size1Button.isSelected()) pos = 0; else
        if (size2Button.isSelected()) pos = 1; else
        if (size3Button.isSelected()) pos = 2; else
        if (size4Button.isSelected()) pos = 3; else
            pos = 4;
        setEditingPreferences(getEditingPreferences().withAlignment(newValues, pos));
        ToolBar.setGridAligment();

		if (redraw && wnd != null)
			wnd.repaint();

        // force repaint of all windows in case the grid spacing changed
        EditWindow.repaintAllContents();
	}

	/**
	 * Method called when the factory reset is requested.
	 */
	public void reset()
	{
		if (User.getFactoryDefGridXSpacing() != User.getDefGridXSpacing())
			User.setDefGridXSpacing(User.getFactoryDefGridXSpacing());
		if (User.getFactoryDefGridYSpacing() != User.getDefGridYSpacing())
			User.setDefGridYSpacing(User.getFactoryDefGridYSpacing());
		if (User.getFactoryDefGridXBoldFrequency() != User.getDefGridXBoldFrequency())
			User.setDefGridXBoldFrequency(User.getFactoryDefGridXBoldFrequency());
		if (User.getFactoryDefGridYBoldFrequency() != User.getDefGridYBoldFrequency())
			User.setDefGridYBoldFrequency(User.getFactoryDefGridYBoldFrequency());
		if (User.isFactoryGridAxesShown() != User.isGridAxesShown())
			User.setGridAxesShown(User.isFactoryGridAxesShown());
		if (User.isFactoryGridAlignMeasurementCursor() != User.isGridAlignMeasurementCursor())
			User.setGridAlignMeasurementCursor(User.isFactoryGridAlignMeasurementCursor());
        setEditingPreferences(getEditingPreferences().withAlignmentReset());
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        alignGroup = new javax.swing.ButtonGroup();
        grid = new javax.swing.JPanel();
        gridPart = new javax.swing.JPanel();
        jLabel32 = new javax.swing.JLabel();
        jLabel33 = new javax.swing.JLabel();
        jLabel34 = new javax.swing.JLabel();
        gridCurrentHoriz = new javax.swing.JTextField();
        gridCurrentVert = new javax.swing.JTextField();
        jLabel35 = new javax.swing.JLabel();
        gridNewHoriz = new javax.swing.JTextField();
        gridNewVert = new javax.swing.JTextField();
        jLabel36 = new javax.swing.JLabel();
        gridBoldHoriz = new javax.swing.JTextField();
        gridBoldVert = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        gridShowAxes = new javax.swing.JCheckBox();
        alignPart = new javax.swing.JPanel();
        gridSizeX5 = new javax.swing.JTextField();
        jLabel38 = new javax.swing.JLabel();
        gridSizeX3 = new javax.swing.JTextField();
        gridSizeX1 = new javax.swing.JTextField();
        size1Button = new javax.swing.JRadioButton();
        size2Button = new javax.swing.JRadioButton();
        size3Button = new javax.swing.JRadioButton();
        size4Button = new javax.swing.JRadioButton();
        size5Button = new javax.swing.JRadioButton();
        gridSizeX2 = new javax.swing.JTextField();
        gridSizeX4 = new javax.swing.JTextField();
        gridSizeY1 = new javax.swing.JTextField();
        gridSizeY2 = new javax.swing.JTextField();
        gridSizeY3 = new javax.swing.JTextField();
        gridSizeY4 = new javax.swing.JTextField();
        gridSizeY5 = new javax.swing.JTextField();
        jLabel37 = new javax.swing.JLabel();
        jLabel39 = new javax.swing.JLabel();
        alignMeasurementCursor = new javax.swing.JCheckBox();

        setTitle("Edit Options");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        grid.setLayout(new java.awt.GridBagLayout());

        gridPart.setBorder(javax.swing.BorderFactory.createTitledBorder("Grid Display"));
        gridPart.setLayout(new java.awt.GridBagLayout());

        jLabel32.setText("Horizontal:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        gridPart.add(jLabel32, gridBagConstraints);

        jLabel33.setText("Vertical:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        gridPart.add(jLabel33, gridBagConstraints);

        jLabel34.setText("Grid dot spacing:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 4, 0, 0);
        gridPart.add(jLabel34, gridBagConstraints);

        gridCurrentHoriz.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        gridPart.add(gridCurrentHoriz, gridBagConstraints);

        gridCurrentVert.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        gridPart.add(gridCurrentVert, gridBagConstraints);

        jLabel35.setText("Default grid spacing:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 4, 0, 0);
        gridPart.add(jLabel35, gridBagConstraints);

        gridNewHoriz.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        gridPart.add(gridNewHoriz, gridBagConstraints);

        gridNewVert.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        gridPart.add(gridNewVert, gridBagConstraints);

        jLabel36.setText("Frequency of bold dots:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 4, 8, 0);
        gridPart.add(jLabel36, gridBagConstraints);

        gridBoldHoriz.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        gridPart.add(gridBoldHoriz, gridBagConstraints);

        gridBoldVert.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        gridPart.add(gridBoldVert, gridBagConstraints);

        jLabel10.setText("(for current window)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 14, 8, 0);
        gridPart.add(jLabel10, gridBagConstraints);

        jLabel13.setText("(for new windows)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 14, 8, 0);
        gridPart.add(jLabel13, gridBagConstraints);

        gridShowAxes.setText("Show X and Y axes");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        gridPart.add(gridShowAxes, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        grid.add(gridPart, gridBagConstraints);

        alignPart.setBorder(javax.swing.BorderFactory.createTitledBorder("Alignment of Cursor to Grid"));
        alignPart.setLayout(new java.awt.GridBagLayout());

        gridSizeX5.setColumns(8);
        gridSizeX5.setPreferredSize(new java.awt.Dimension(40, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        alignPart.add(gridSizeX5, gridBagConstraints);

        jLabel38.setText("Values of zero will cause no alignment");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        alignPart.add(jLabel38, gridBagConstraints);

        gridSizeX3.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        alignPart.add(gridSizeX3, gridBagConstraints);

        gridSizeX1.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        alignPart.add(gridSizeX1, gridBagConstraints);

        alignGroup.add(size1Button);
        size1Button.setText("Size 1 (largest)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        alignPart.add(size1Button, gridBagConstraints);

        alignGroup.add(size2Button);
        size2Button.setText("Size 2");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        alignPart.add(size2Button, gridBagConstraints);

        alignGroup.add(size3Button);
        size3Button.setText("Size 3");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        alignPart.add(size3Button, gridBagConstraints);

        alignGroup.add(size4Button);
        size4Button.setText("Size 4");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        alignPart.add(size4Button, gridBagConstraints);

        alignGroup.add(size5Button);
        size5Button.setText("Size 5 (smallest)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        alignPart.add(size5Button, gridBagConstraints);

        gridSizeX2.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        alignPart.add(gridSizeX2, gridBagConstraints);

        gridSizeX4.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        alignPart.add(gridSizeX4, gridBagConstraints);

        gridSizeY1.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 2, 4);
        alignPart.add(gridSizeY1, gridBagConstraints);

        gridSizeY2.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        alignPart.add(gridSizeY2, gridBagConstraints);

        gridSizeY3.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        alignPart.add(gridSizeY3, gridBagConstraints);

        gridSizeY4.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        alignPart.add(gridSizeY4, gridBagConstraints);

        gridSizeY5.setColumns(8);
        gridSizeY5.setPreferredSize(new java.awt.Dimension(40, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 4, 4);
        alignPart.add(gridSizeY5, gridBagConstraints);

        jLabel37.setText("Horizontal:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        alignPart.add(jLabel37, gridBagConstraints);

        jLabel39.setText("Vertical:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 2, 4);
        alignPart.add(jLabel39, gridBagConstraints);

        alignMeasurementCursor.setText("Grid align cursor in \"Measurement\" mode");
        alignMeasurementCursor.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alignMeasurementCursorActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 3;
        alignPart.add(alignMeasurementCursor, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        grid.add(alignPart, gridBagConstraints);

        getContentPane().add(grid, new java.awt.GridBagConstraints());

        pack();
    }// </editor-fold>//GEN-END:initComponents

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    private void alignMeasurementCursorActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alignMeasurementCursorActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_alignMeasurementCursorActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup alignGroup;
    private javax.swing.JCheckBox alignMeasurementCursor;
    private javax.swing.JPanel alignPart;
    private javax.swing.JPanel grid;
    private javax.swing.JTextField gridBoldHoriz;
    private javax.swing.JTextField gridBoldVert;
    private javax.swing.JTextField gridCurrentHoriz;
    private javax.swing.JTextField gridCurrentVert;
    private javax.swing.JTextField gridNewHoriz;
    private javax.swing.JTextField gridNewVert;
    private javax.swing.JPanel gridPart;
    private javax.swing.JCheckBox gridShowAxes;
    private javax.swing.JTextField gridSizeX1;
    private javax.swing.JTextField gridSizeX2;
    private javax.swing.JTextField gridSizeX3;
    private javax.swing.JTextField gridSizeX4;
    private javax.swing.JTextField gridSizeX5;
    private javax.swing.JTextField gridSizeY1;
    private javax.swing.JTextField gridSizeY2;
    private javax.swing.JTextField gridSizeY3;
    private javax.swing.JTextField gridSizeY4;
    private javax.swing.JTextField gridSizeY5;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel35;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabel37;
    private javax.swing.JLabel jLabel38;
    private javax.swing.JLabel jLabel39;
    private javax.swing.JRadioButton size1Button;
    private javax.swing.JRadioButton size2Button;
    private javax.swing.JRadioButton size3Button;
    private javax.swing.JRadioButton size4Button;
    private javax.swing.JRadioButton size5Button;
    // End of variables declaration//GEN-END:variables

}
