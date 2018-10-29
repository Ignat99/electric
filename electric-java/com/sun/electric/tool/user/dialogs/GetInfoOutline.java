/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: GetInfoOutline.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.ClientOS;

import java.awt.Frame;
import java.awt.geom.Point2D;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * Class to handle the "GetInfoOutline" dialog.
 */
public class GetInfoOutline extends EModelessDialog implements HighlightListener, DatabaseChangeListener
{
    private NodeInst ni;
	private JList list;
	private DefaultListModel model;
	private boolean changingCoordinates = false;
	private static GetInfoOutline theDialog;

	public static void showOutlinePropertiesDialog()
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
            theDialog = new GetInfoOutline(jf);
        }
        theDialog.loadDialog();
        if (!theDialog.isVisible())
		{
        	theDialog.pack();
        	theDialog.ensureProperSize();
    		theDialog.setVisible(true);
		}
		theDialog.toFront();
	}

	/** Creates new form GetInfoOutline */
	public GetInfoOutline(Frame parent)
	{
		super(parent);
		initComponents();
        getRootPane().setDefaultButton(apply);

		// make all text fields select-all when entered
	    EDialog.makeTextFieldSelectAllOnTab(xValue);
	    EDialog.makeTextFieldSelectAllOnTab(yValue);

        UserInterfaceMain.addDatabaseChangeListener(this);
        Highlighter.addHighlightListener(this);

        // make a list of vertices
		model = new DefaultListModel();
		list = new JList(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		pointPane.setViewportView(list);
		list.addListSelectionListener(new ListSelectionListener()
		{
			public void valueChanged(ListSelectionEvent evt) { listClick(); }
		});
		xValue.getDocument().addDocumentListener(new OutlineDocumentListener(this));
		yValue.getDocument().addDocumentListener(new OutlineDocumentListener(this));
		finishInitialization();
	}

	private void loadDialog()
	{
        // presume a dead dialog
		apply.setEnabled(false);
		deletePoint.setEnabled(false);
		duplicatePoint.setEnabled(false);
		jLabel1.setEnabled(false);
		jLabel2.setEnabled(false);
		pointPane.setEnabled(false);
		xValue.setEditable(false);
		yValue.setEditable(false);

		// must have a node
        EditWindow wnd = EditWindow.getCurrent();
        if (wnd == null) // not an edit window. Eg 3D ViewWindows
            return;
        ni = (NodeInst)wnd.getHighlighter().getOneElectricObject(NodeInst.class);
		if (ni == null) return;

		// node must have coordinates
		Point2D [] points = ni.getTrace();
		if (points == null) return;

		// make the dialog live
		apply.setEnabled(true);
		deletePoint.setEnabled(true);
		duplicatePoint.setEnabled(true);
		jLabel1.setEnabled(true);
		jLabel2.setEnabled(true);
		pointPane.setEnabled(true);
		xValue.setEditable(true);
		yValue.setEditable(true);

		// load the coordinates
		Point2D [] dbSpacePoints = new Point2D[points.length];
		for(int i=0; i<points.length; i++)
		{
			if (points[i] != null)
				dbSpacePoints[i] = new Point2D.Double(points[i].getX() + ni.getAnchorCenterX(),
					points[i].getY() + ni.getAnchorCenterY());
		}
		loadList(dbSpacePoints, 0);
		listClick();
	}

	private void loadList(Point2D [] newPoints, int index)
	{
		model.clear();
		for(int i=0; i<newPoints.length; i++)
			model.addElement(makeLine(i, newPoints[i]));
		if (index >= 0) list.setSelectedIndex(index);
	}

	protected void escapePressed() { done(null); }

	private void listClick()
	{
		String line = (String)list.getSelectedValue();
		if (line == null) return;
		changingCoordinates = true;
		Point2D clickedValue = getPointValue(line);
		if (clickedValue == null)
		{
			xValue.setText("");
			yValue.setText("");
		} else
		{
			Technology tech = ni.getParent().getTechnology();
			xValue.setText(TextUtils.formatDistance(clickedValue.getX(), tech));
			yValue.setText(TextUtils.formatDistance(clickedValue.getY(), tech));
		}
		changingCoordinates = false;
	}

	private String makeLine(int index, Point2D pt)
	{
		if (pt == null) return "-------------";
		Technology tech = ni.getParent().getTechnology();
		return index + ": (" +
			TextUtils.formatDistance(pt.getX(), tech) + ", " +
			TextUtils.formatDistance(pt.getY(), tech) + ")";
	}

	/**
	 * Method called when the user types a new coordinate value into the edit field.
	 */
	private void coordinatesChanged()
	{
		if (changingCoordinates) return;
		Technology tech = ni.getParent().getTechnology();
		Point2D typedValue = new Point2D.Double(TextUtils.atofDistance(xValue.getText(), tech),
			TextUtils.atofDistance(yValue.getText(), tech));
		int index = list.getSelectedIndex();
		model.set(index, makeLine(index, typedValue));
	}

	private Point2D getPointValue(String line)
	{
		double xV = 0, yV = 0;
		int openPos = line.indexOf('(');
		int commaPos = line.indexOf(',', openPos);
		int closePos = line.indexOf(')', commaPos);
		if (openPos >= 0 && commaPos >= 0 && closePos >= 0)
		{
			xV = TextUtils.atofDistance(line.substring(openPos+1, commaPos));
			yV = TextUtils.atofDistance(line.substring(commaPos+1, closePos));
			return new Point2D.Double(xV, yV);
		}
		return null;
	}

	/**
	 * Class to handle special changes to changes to a CIF layer.
	 */
	private static class OutlineDocumentListener implements DocumentListener
	{
		private GetInfoOutline dialog;

		OutlineDocumentListener(GetInfoOutline dialog) { this.dialog = dialog; }

		public void changedUpdate(DocumentEvent e) { dialog.coordinatesChanged(); }
		public void insertUpdate(DocumentEvent e) { dialog.coordinatesChanged(); }
		public void removeUpdate(DocumentEvent e) { dialog.coordinatesChanged(); }
	}

    /**
     * Reloads the dialog when Highlights change
     */
    public void highlightChanged(Highlighter which)
	{
        if (!isVisible()) return;
        loadDialog();
	}

    /**
     * Called when by a Highlighter when it loses focus. The argument
     * is the Highlighter that has gained focus (may be null).
     * @param highlighterGainedFocus the highlighter for the current window (may be null).
     */
    public void highlighterLostFocus(Highlighter highlighterGainedFocus)
    {
        if (!isVisible()) return;
        loadDialog();
    }

    /**
     * Respond to database changes
     * @param e database change event
     */
    public void databaseChanged(DatabaseChangeEvent e)
    {
        if (!isVisible()) return;

        // check if we care about the changes
        if (e.objectChanged(ni)) loadDialog();
    }

//     /**
//      * Respond to database changes
//      * @param batch a batch of changes completed
//      */
//     public void databaseEndChangeBatch(Undo.ChangeBatch batch)
//     {
//         if (!isVisible()) return;

//         // check if we care about the changes
//         boolean reload = false;
//         for (Iterator it = batch.getChanges(); it.hasNext(); )
//         {
//             Undo.Change change = it.next();
//             ElectricObject obj = change.getObject();
//             if (obj == ni)
//             {
//                 reload = true;
//                 break;
//             }
//         }
//         if (reload) loadDialog();
//     }

//     /** Don't do anything on little database changes, only after all database changes */
//     public void databaseChanged(Undo.Change change) {}

//     /** This is a GUI listener */
//     public boolean isGUIListener() { return true; }

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc=" Generated Code ">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        done = new javax.swing.JButton();
        pointPane = new javax.swing.JScrollPane();
        apply = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        xValue = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        yValue = new javax.swing.JTextField();
        deletePoint = new javax.swing.JButton();
        duplicatePoint = new javax.swing.JButton();

        getContentPane().setLayout(new java.awt.GridBagLayout());

        setTitle("Outline Properties");
        setName("");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        done.setText("Done");
        done.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                done(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(done, gridBagConstraints);

        pointPane.setMinimumSize(new java.awt.Dimension(100, 150));
        pointPane.setPreferredSize(new java.awt.Dimension(100, 150));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(pointPane, gridBagConstraints);

        apply.setText("Apply");
        apply.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(apply, gridBagConstraints);

        jLabel1.setText("X:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel1, gridBagConstraints);

        xValue.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(xValue, gridBagConstraints);

        jLabel2.setText("Y:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(jLabel2, gridBagConstraints);

        yValue.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(yValue, gridBagConstraints);

        deletePoint.setText("Delete Point");
        deletePoint.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deletePointActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(deletePoint, gridBagConstraints);

        duplicatePoint.setText("Duplicate Point");
        duplicatePoint.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                duplicatePointActionPerformed(evt);
            }
        });

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(duplicatePoint, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void applyActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_applyActionPerformed
	{//GEN-HEADEREND:event_applyActionPerformed
		int newPointCount = model.size();
		EPoint [] pts = new EPoint[newPointCount];
		for(int i=0; i<newPointCount; i++)
		{
			String line = (String)model.get(i);
			Point2D pt = getPointValue(line);
			if (pt != null)
				pts[i] = EPoint.fromLambda(pt.getX(), pt.getY());
		}
		new AdjustOutline(ni, pts);
	}//GEN-LAST:event_applyActionPerformed

	private void duplicatePointActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_duplicatePointActionPerformed
	{//GEN-HEADEREND:event_duplicatePointActionPerformed
		int index = list.getSelectedIndex();

		// redraw the entire list so that the indices are correct
		int newPointCount = model.size();
		Point2D [] newPoints = new Point2D[newPointCount+1];
		int j = 0;
		for(int i=0; i<newPointCount; i++)
		{
			String line = (String)model.get(i);
			if (i == index)
			{
				Point2D insertPt = getPointValue(line);
				if (insertPt != null)
					newPoints[j++] = insertPt;
			}
			newPoints[j++] = getPointValue(line);
		}

		loadList(newPoints, index);

	}//GEN-LAST:event_duplicatePointActionPerformed

	private void deletePointActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deletePointActionPerformed
	{//GEN-HEADEREND:event_deletePointActionPerformed
		int index = list.getSelectedIndex();

		// redraw the entire list so that the indices are correct
		int newPointCount = model.size();
		Point2D [] newPoints = new Point2D[newPointCount-1];
		int j = 0;
		for(int i=0; i<newPointCount; i++)
		{
			if (i == index) continue;
			String line = (String)model.get(i);
			newPoints[j++] = getPointValue(line);
		}

		if (index >= newPointCount) index--;
		loadList(newPoints, index);
	}//GEN-LAST:event_deletePointActionPerformed

	private void done(java.awt.event.ActionEvent evt)//GEN-FIRST:event_done
	{//GEN-HEADEREND:event_done
		closeDialog(null);
	}//GEN-LAST:event_done

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
        super.closeDialog();
	}//GEN-LAST:event_closeDialog

	/**
	 * Class to GetInfoOutline a cell in a new thread.
	 */
	private static class AdjustOutline extends Job
	{
		private NodeInst ni;
		private EPoint [] pts;

		private AdjustOutline(NodeInst ni, EPoint [] pts)
		{
			super("Adjust Outline", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.ni = ni;
			this.pts = pts;
			startJob();
		}

		/**
		 * Method to change the coordinates.
		 */
        @Override
		public boolean doIt() throws JobException
		{
			ni.setTrace(pts);
			return true;
		}
	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton apply;
    private javax.swing.JButton deletePoint;
    private javax.swing.JButton done;
    private javax.swing.JButton duplicatePoint;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane pointPane;
    private javax.swing.JTextField xValue;
    private javax.swing.JTextField yValue;
    // End of variables declaration//GEN-END:variables

}
