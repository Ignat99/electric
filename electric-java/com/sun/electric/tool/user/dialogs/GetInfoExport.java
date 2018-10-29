/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GetInfoExport.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.ClientOS;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JFrame;

/**
 * Class to handle the "Export Get-Info" dialog.
 */
public class GetInfoExport extends EModelessDialog implements HighlightListener, DatabaseChangeListener
{
	private static GetInfoExport theDialog = null;
	private Export shownExport;
	private String initialName;
	private String initialRefName;
	private String initialCharacteristicName;
	private boolean initialBodyOnly, initialAlwaysDrawn;
    private TextInfoPanel textPanel;

	/**
	 * Method to show the Export Get-Info dialog.
	 */
	public static void showDialog()
	{
        if (ClientOS.isOSLinux()) {
            // JKG 07Apr2006:
            // On Linux, if a dialog is built, closed using setVisible(false),
            // and then requested again using setVisible(true), it does
            // not appear on top. I've tried using toFront(), requestFocus(),
            // but none of that works.  Instead, I brute force it and
            // rebuild the dialog from scratch each time.
            if (theDialog != null) theDialog.dispose();
            theDialog = null;
        }
		if (theDialog == null)
		{
			JFrame jf = null;
            if (TopLevel.isMDIMode()) jf = TopLevel.getCurrentJFrame();
            theDialog = new GetInfoExport(jf);
		}
        theDialog.loadExportInfo();
        if (!theDialog.isVisible())
		{
        	theDialog.pack();
        	theDialog.ensureProperSize();
    		theDialog.setVisible(true);
		}
		theDialog.toFront();
	}

    /**
     * Reloads the dialog when Highlights change
     */
    public void highlightChanged(Highlighter which)
	{
        if (!isVisible()) return;
		loadExportInfo();
	}

    /**
     * Called when by a Highlighter when it loses focus. The argument
     * is the Highlighter that has gained focus (may be null).
     * @param highlighterGainedFocus the highlighter for the current window (may be null).
     */
    public void highlighterLostFocus(Highlighter highlighterGainedFocus) {
        if (!isVisible()) return;
        loadExportInfo();
    }

    /**
     * Respond to database changes
     * @param e database change event
     */
    public void databaseChanged(DatabaseChangeEvent e) {
        if (!isVisible()) return;

        // update dialog if we care about the changes
		if (e.objectChanged(shownExport))
            loadExportInfo();
    }

	private void loadExportInfo()
	{
        // update current window
        EditWindow curWnd = EditWindow.getCurrent();
        if (curWnd == null)
        {
            disableDialog();
            return;
        }

		// must have a single export selected
		Export pp = null;
		int exportCount = 0;
        for(Highlight h : curWnd.getHighlighter().getHighlights())
        {
            if (!h.isHighlightText()) continue;
            if (h.getVarKey() != Export.EXPORT_NAME) continue;
            ElectricObject eobj = h.getElectricObject();
            if (eobj instanceof Export)
            {
                pp = (Export)eobj;
                exportCount++;
            }
        }
		if (exportCount > 1) pp = null;

        boolean enabled = true;
        if (pp == null) enabled = false;

        EDialog.focusClearOnTextField(theText);

        // set enabled state of dialog
        theText.setEditable(enabled);
        bodyOnly.setEnabled(enabled);
        alwaysDrawn.setEnabled(enabled);
        characteristics.setEnabled(enabled);
        refName.setEditable(enabled);

        if (!enabled) {
            disableDialog();
			return;
		}

        // set name
		initialName = pp.getName();
		theText.setText(initialName);

		// set location
		Poly poly = pp.getPoly();
		Technology tech = curWnd.getCell().getTechnology();
		centerLoc.setText("Center: (" + TextUtils.formatDistance(poly.getCenterX(), tech) + "," +
			TextUtils.formatDistance(poly.getCenterY(), tech) + ")");

		// set Body and Always Drawn check boxes
		initialBodyOnly = pp.isBodyOnly();
		bodyOnly.setSelected(initialBodyOnly);
		initialAlwaysDrawn = pp.isAlwaysDrawn();
		alwaysDrawn.setSelected(initialAlwaysDrawn);

        // set characteristic and reference name
		PortCharacteristic initialCharacteristic = pp.getCharacteristic();
		initialCharacteristicName = initialCharacteristic.getName();
		characteristics.setSelectedItem(initialCharacteristicName);
		initialRefName = "";
		if (initialCharacteristic == PortCharacteristic.REFBASE ||
			initialCharacteristic == PortCharacteristic.REFIN ||
			initialCharacteristic == PortCharacteristic.REFOUT)
		{
			Variable var = pp.getVar(Export.EXPORT_REFERENCE_NAME);
			if (var != null)
				initialRefName = var.describe(-1);
			refName.setEditable(true);
		} else
		{
			refName.setEditable(false);
		}
		refName.setText(initialRefName);

        // set text info panel
        textPanel.setTextDescriptor(Export.EXPORT_NAME, pp);

		shownExport = pp;
        EDialog.focusOnTextField(theText);
	}

    private void disableDialog() {
        shownExport = null;
        theText.setText("");
        refName.setText("");
		centerLoc.setText("");
        textPanel.setTextDescriptor(null, null);
    }

	/** Creates new form Export Get-Info */
	private GetInfoExport(Frame parent)
	{
		super(parent);
		initComponents();
        getRootPane().setDefaultButton(ok);

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(refName);
	    EDialog.makeTextFieldSelectAllOnTab(theText);

        // add myself as a listener for highlight changes
        UserInterfaceMain.addDatabaseChangeListener(this);
        Highlighter.addHighlightListener(this);

        // set characteristic combo box
		List<PortCharacteristic> chars = PortCharacteristic.getOrderedCharacteristics();
		for(PortCharacteristic ch : chars)
		{
			characteristics.addItem(ch.getName());
		}

        // add textPanel
        textPanel = new TextInfoPanel(false, false);
        java.awt.GridBagConstraints gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(textPanel, gridBagConstraints);
        pack();

		loadExportInfo();
		finishInitialization();
	}

	protected void escapePressed() { cancelActionPerformed(null); }

	private static class ChangeExport extends Job
	{
		private Export pp;
		private String oldName;
		private String newName;
		private boolean newBodyOnly, newAlwaysDrawn;
		private String newCharName;
		private String newRefName;

		protected ChangeExport(Export pp,
                String oldName,
                String newName,
                boolean newBodyOnly, boolean newAlwaysDrawn,
                String newCharName, String newRefName)
		{
			super("Modify Export", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.pp = pp;
            this.oldName = oldName;
			this.newName = newName;
            this.newBodyOnly = newBodyOnly;
            this.newAlwaysDrawn = newAlwaysDrawn;
            this.newCharName = newCharName;
            this.newRefName = newRefName;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
		    // change the name
			if (!oldName.equals(newName)) pp.rename(newName);

			// change the body-only
			pp.setBodyOnly(newBodyOnly);

			// change always drawn
			pp.setAlwaysDrawn(newAlwaysDrawn);

			// change the characteristic
	        PortCharacteristic newChar = PortCharacteristic.findCharacteristic(newCharName);
	        if (newChar != pp.getCharacteristic())
	        {
	        	pp.setCharacteristic(newChar);

	        	// change characteristic in all views
	        	List<Cell> othersChanged = new ArrayList<Cell>();
	        	Cell thisCell = pp.getParent();
	        	for(Cell otherCell : thisCell.getCellsInGroup())
	        	{
	        		if (otherCell == thisCell) continue;

	        		List<Export> otherPPs = pp.findAllEquivalents(otherCell, true);
	        		for(Export otherPP : otherPPs)
	        		{
	        			if (otherPP.getCharacteristic() != newChar)
	        			{
	        				otherPP.setCharacteristic(newChar);
	        				othersChanged.add(otherCell);
	        			}
	        		}
	        	}
	        	if (othersChanged.size() > 0)
	        	{
	        		System.out.print("Also changed the characteristic of this export in");
	        		for(int i=0; i<othersChanged.size(); i++)
	        		{
	        			Cell otherCell = othersChanged.get(i);
	        			if (i != 0) System.out.print(",");
	        			System.out.print(" " + otherCell.describe(false));
	        		}
	        		System.out.println();
	        	}
	        }

            // change reference name
			if (newChar.isReference())
				pp.newVar(Export.EXPORT_REFERENCE_NAME, newRefName, ep);
			return true;
		}
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        cancel = new javax.swing.JButton();
        ok = new javax.swing.JButton();
        apply = new javax.swing.JButton();
        leftSide = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        characteristics = new javax.swing.JComboBox();
        jLabel1 = new javax.swing.JLabel();
        refName = new javax.swing.JTextField();
        bodyOnly = new javax.swing.JCheckBox();
        alwaysDrawn = new javax.swing.JCheckBox();
        centerLoc = new javax.swing.JLabel();
        header = new javax.swing.JLabel();
        theText = new javax.swing.JTextField();

        setTitle("Export Properties");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        cancel.setText("Cancel");
        cancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(cancel, gridBagConstraints);

        ok.setText("OK");
        ok.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(ok, gridBagConstraints);

        apply.setText("Apply");
        apply.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.weightx = 0.25;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(apply, gridBagConstraints);

        leftSide.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        leftSide.setLayout(new java.awt.GridBagLayout());

        jLabel10.setText("Characteristics:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        leftSide.add(jLabel10, gridBagConstraints);

        characteristics.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                characteristicsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        leftSide.add(characteristics, gridBagConstraints);

        jLabel1.setText("Reference name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        leftSide.add(jLabel1, gridBagConstraints);

        refName.setText(" ");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        leftSide.add(refName, gridBagConstraints);

        bodyOnly.setText("Body only");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        leftSide.add(bodyOnly, gridBagConstraints);

        alwaysDrawn.setText("Always drawn");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        leftSide.add(alwaysDrawn, gridBagConstraints);

        centerLoc.setText("Center:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        leftSide.add(centerLoc, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 4, 2, 4);
        getContentPane().add(leftSide, gridBagConstraints);

        header.setText("Export name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(header, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(theText, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void characteristicsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_characteristicsActionPerformed
	{//GEN-HEADEREND:event_characteristicsActionPerformed
		String stringNow = (String)characteristics.getSelectedItem();
		PortCharacteristic ch = PortCharacteristic.findCharacteristic(stringNow);
		refName.setEditable(ch.isReference());
	}//GEN-LAST:event_characteristicsActionPerformed

	private void applyActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_applyActionPerformed
	{//GEN-HEADEREND:event_applyActionPerformed
		if (shownExport == null) return;

        // check if changes to be made
        boolean changed = false;

        // check name
        String newName = theText.getText();
        if (!newName.equals(initialName)) changed = true;
        // check body only
        boolean newBodyOnly = bodyOnly.isSelected();
        if (newBodyOnly != initialBodyOnly) changed = true;
        // check always drawn
        boolean newAlwaysDrawn = alwaysDrawn.isSelected();
        if (newAlwaysDrawn != initialAlwaysDrawn) changed = true;
        // check characteristic
        String newCharName = (String)characteristics.getSelectedItem();
        if (!newCharName.equals(initialCharacteristicName)) changed = true;
        // check reference name
        String newRefName = refName.getText();
        if (!newRefName.equals(initialRefName)) changed = true;

        if (changed) {
            // generate Job to change export port options
            new ChangeExport(shownExport, initialName, newName, newBodyOnly, newAlwaysDrawn, newCharName, newRefName);
        }

        // possibly generate job to change export text options
        textPanel.applyChanges(true);

        initialName = newName;
        initialBodyOnly = newBodyOnly;
        initialAlwaysDrawn = newAlwaysDrawn;
        initialCharacteristicName = newCharName;
        initialRefName = newRefName;

	}//GEN-LAST:event_applyActionPerformed

	private void okActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_okActionPerformed
	{//GEN-HEADEREND:event_okActionPerformed
		applyActionPerformed(evt);
		closeDialog(null);
	}//GEN-LAST:event_okActionPerformed

	private void cancelActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelActionPerformed
	{//GEN-HEADEREND:event_cancelActionPerformed
		closeDialog(null);
	}//GEN-LAST:event_cancelActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
        super.closeDialog();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox alwaysDrawn;
    private javax.swing.JButton apply;
    private javax.swing.JCheckBox bodyOnly;
    private javax.swing.JButton cancel;
    private javax.swing.JLabel centerLoc;
    private javax.swing.JComboBox characteristics;
    private javax.swing.JLabel header;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JPanel leftSide;
    private javax.swing.JButton ok;
    private javax.swing.JTextField refName;
    private javax.swing.JTextField theText;
    // End of variables declaration//GEN-END:variables

}
