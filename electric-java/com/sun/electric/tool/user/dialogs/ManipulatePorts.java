/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ManipulatePorts.java
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
package com.sun.electric.tool.user.dialogs;

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ExportChanges;
import com.sun.electric.tool.user.HighlightListener;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.util.TextUtils;

import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * Class to handle the "Manipulate Ports" dialog.
 */
public class ManipulatePorts extends EDialog implements HighlightListener, DatabaseChangeListener
{
	private static final String [] columnNames = {"Check", "Name", "Characteristic", "Connections", "Arcs", "Exports"};
	private static int sortColumn = 1;
	private static boolean sortAscending = true;

	private PortsTable portTable;
	private ColumnListener columnListener;
	private NodeInst currentNode;
	private List<Export> exportsOnNode = new ArrayList<Export>();

	public static void showDialog()
	{
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;
		Highlighter h = wnd.getHighlighter();
		NodeInst ni = (NodeInst)h.getOneElectricObject(NodeInst.class);
		if (ni == null) return;
		new ManipulatePorts(TopLevel.getCurrentJFrame(), ni);
	}

	/** Creates new form Manipulate Ports */
	private ManipulatePorts(Frame parent, NodeInst ni)
	{
		super(parent, false);
		initComponents();
		getRootPane().setDefaultButton(done);

		portTable = new PortsTable(ni);
		JTableHeader header = portTable.getTableHeader();
		columnListener = new ColumnListener();
		header.addMouseListener(columnListener);
		portPane.setViewportView(portTable);
		finishInitialization();
		setVisible(true);
        UserInterfaceMain.addDatabaseChangeListener(this);
		Highlighter.addHighlightListener(this);
	}

	private class ColumnListener extends MouseAdapter
	{
		public void mouseClicked(MouseEvent e)
		{
			TableColumnModel colModel = portTable.getColumnModel();
			int colNumber = colModel.getColumnIndexAtX(e.getX());
			int modelIndex = colModel.getColumn(colNumber).getModelIndex();
			if (modelIndex < 0) return;
			if (sortColumn == modelIndex) sortAscending = !sortAscending; else
			{
				sortColumn = modelIndex;
				sortAscending = true;
			}
			PortTableModel model = portTable.getModel();
			model.sortTable();
			model.fireTableStructureChanged();
		}
	}

	private class PortsTable extends JTable
	{
		private PortTableModel model;

		/**
		 * Constructor for PortsTable
		 */
		public PortsTable(NodeInst ni)
		{
			model = new PortTableModel(ni);
			setModel(model);
			TableColumn tc = getColumn(getColumnName(0));
			if (tc != null) tc.setPreferredWidth(60);
			tc = getColumn(getColumnName(1));
			if (tc != null) tc.setPreferredWidth(120);
			tc = getColumn(getColumnName(2));
			if (tc != null) tc.setPreferredWidth(100);
			tc = getColumn(getColumnName(3));
			if (tc != null) tc.setPreferredWidth(90);
			tc = getColumn(getColumnName(4));
			if (tc != null) tc.setPreferredWidth(50);
			tc = getColumn(getColumnName(5));
			if (tc != null) tc.setPreferredWidth(120);
			loadPorts(ni);
		}

		public void loadPorts(NodeInst ni)
		{
			currentNode = ni;
			exportsOnNode.clear();
			model.clearAll();
			if (ni == null)
			{
				title.setText("Port list (no selected node)");
			} else
			{
				title.setText("Ports on node " + ni.describe(false));
				for(Iterator<Export> it = ni.getExports(); it.hasNext(); )
					exportsOnNode.add(it.next());
				for(Iterator<PortInst> it = ni.getPortInsts(); it.hasNext(); )
				{
					PortInst pi = it.next();
					model.newVar(pi);
				}
			}
			model.sortTable();
		}

		public void toggleChecks()
		{
			int [] rows = getSelectedRows();
			for(int i=0; i<rows.length; i++)
			{
				Boolean b = (Boolean)model.getValueAt(rows[i], 0);
				model.setValueAt(Boolean.valueOf(!b.booleanValue()), rows[i], 0);
			}
		}

		public PortTableModel getModel() { return model; }

		public void checkAll() { model.checkAll(); }

		public void uncheckAll() { model.uncheckAll(); }

		public void reExportChecked() { model.reExportChecked(); }

		public void unExportChecked() { model.unExportChecked(); }

		public void showChecked() { model.showChecked(); }

		public void highlightChecked() { model.highlightChecked(); }
	}

	/**
	 * Model for storing Table data
	 */
	private class PortTableModel extends AbstractTableModel
	{
		private NodeInst ni;
		private List<PortEntry> ports;

		/**
		 * Class to sort ports.
		 */
		private class PortEntrySort implements Comparator<PortEntry>
		{
			public int compare(PortEntry p1, PortEntry p2)
			{
				if (!sortAscending)
				{
					PortEntry swap = p1;
					p1 = p2;
					p2 = swap;
				}
				String s1 = null, s2 = null;
				switch (sortColumn)
				{
					case 0:
						boolean b1 = p1.isSelected();
						boolean b2 = p2.isSelected();
						if (b1 == b2) return 0;
						if (b1) return 1;
						return -1;
					case 1:
						s1 = p1.getPort().getPortProto().getName();
						s2 = p2.getPort().getPortProto().getName();
						break;
					case 2:
						s1 = p1.getPort().getPortProto().getCharacteristic().getName();
						s2 = p2.getPort().getPortProto().getCharacteristic().getName();
						return s1.compareTo(s2);
					case 3:
						s1 = p1.getConnections(ni);
						s2 = p2.getConnections(ni);
						break;
					case 4:
						int i1 = p1.getNumArcs();
						int i2 = p2.getNumArcs();
						if (i1 == 12) return 0;
						if (i1 < i2) return 1;
						return -1;
					case 5:
						s1 = p1.getExports();
						s2 = p2.getExports();
						break;
				}
				return TextUtils.STRING_NUMBER_ORDER.compare(s1, s2);
			}
		}

		// constructor
		private PortTableModel(NodeInst ni)
		{
			this.ni = ni;
			ports = new ArrayList<PortEntry>();
		}

		public void clearAll()
		{
			ports.clear();
		}

		/**
		 * Create a new var with default properties
		 */
		public void newVar(PortInst pi)
		{
			PortEntry ve = new PortEntry(pi);
			ports.add(ve);
		}

		public void sortTable()
		{
			Collections.sort(ports, new PortEntrySort());
			fireTableDataChanged();
		}

		public void checkAll()
		{
			int i = 0;
			for(PortEntry pe : ports)
			{
				pe.setSelected(true);
				fireTableCellUpdated(i++, 0);
			}
		}

		public void uncheckAll()
		{
			int i = 0;
			for(PortEntry pe : ports)
			{
				pe.setSelected(false);
				fireTableCellUpdated(i++, 0);
			}
		}

		public void reExportChecked()
		{
			List<PortInst> queuedExports = new ArrayList<PortInst>();
			for(PortEntry pe : ports)
			{
				if (!pe.isSelected()) continue;
				queuedExports.add(pe.getPort());
			}
	        new ExportChanges.ReExportPorts(ni.getParent(), queuedExports, true, true, true,
	        	false, User.isIncrementRightmostIndex(), null);
		}

		public void unExportChecked()
		{
			List<Export> queuedExports = new ArrayList<Export>();
			for(PortEntry pe : ports)
			{
				if (!pe.isSelected()) continue;
				for(Iterator<Export> eIt = pe.getPort().getExports(); eIt.hasNext(); )
				{
					Export e = eIt.next();
					queuedExports.add(e);
				}
			}
			ExportChanges.deleteExports(ni.getParent(), queuedExports);
		}

		public void showChecked()
		{
			List<PortInst> portList = new ArrayList<PortInst>();
			for(PortEntry pe : ports)
			{
				if (!pe.isSelected()) continue;
				portList.add(pe.getPort());
			}
			ExportChanges.showPorts(portList);
		}

		public void highlightChecked()
		{
			UserInterface ui = Job.getUserInterface();
			EditWindow_ wnd = ui.getCurrentEditWindow_();
			if (wnd == null) return;
			wnd.clearHighlighting();
			for(PortEntry pe : ports)
			{
				if (!pe.isSelected()) continue;
				wnd.addElectricObject(pe.getPort(), ni.getParent());
			}
			wnd.finishedHighlighting();
		}

		/** Method to get the number of columns. */
		public int getColumnCount() { return 6; }

		/** Method to get the number of rows. */
		public int getRowCount() { return ports.size(); }

		/** Method to get a location in the table. */
		public Object getValueAt(int rowIndex, int columnIndex) {

			PortEntry pe = ports.get(rowIndex);
			if (pe == null) return null;

			switch (columnIndex)
			{
				// selected
				case 0: return Boolean.valueOf(pe.isSelected());

				// name
				case 1: return pe.getPort().getPortProto().getName();

				// characteristic
				case 2: return pe.getPort().getPortProto().getCharacteristic().getName();
	
				// connections
				case 3: return pe.getConnections(ni);
	
				// arcs
				case 4: return Integer.toString(pe.getNumArcs());
	
				// exports
				case 5: return pe.getExports();
			}
			return null;
		}

		/** Method to get a column's header name. */
		public String getColumnName(int col)
		{
			String colName = columnNames[col];
			if (col == sortColumn)
			{
				if (sortAscending) colName += " \u21D3"; else
					colName += " \u21D1";
			}
			return colName;
		}

		/** Method to determine whether a cell is editable. */
		public boolean isCellEditable(int row, int col)
		{
			if (col == 0) return true;
			return false;
		}

		/** Method to set a value. */
		public void setValueAt(Object aValue, int row, int col)
		{
			PortEntry ve = ports.get(row);
			if (ve == null) return;

			if (col != 0) return;

			Boolean b = (Boolean)aValue;
			if (ve.isSelected() != b.booleanValue())
			{
				ve.setSelected(b.booleanValue());
				fireTableCellUpdated(row, col);
			}
		}

		public Class<?> getColumnClass(int col)
		{
			if (col == 0) return Boolean.class;
			return String.class;
		}
	}

	private class PortEntry
	{
		private boolean selected;
		private PortInst pi;

		public PortEntry(PortInst pi) { this.pi = pi; }

		public PortInst getPort() { return pi; }

		public boolean isSelected() { return selected; }

		public void setSelected(boolean s) { selected = s; }

		public String getConnections(NodeInst ni)
		{
			ArcProto [] conns = pi.getPortProto().getBasePort().getConnections();
			StringBuffer buf = new StringBuffer();
			for(int i=0; i<conns.length; i++)
			{
				if (conns[i].getTechnology() == Generic.tech())
				{
					if (ni.getProto().getTechnology() != Generic.tech()) continue;
				}
				if (buf.length() > 0) buf.append(", ");
				buf.append(conns[i].getName());
			}
			return buf.toString();
		}

		public int getNumArcs()
		{
			int num = 0;
			for(Iterator<Connection> it = pi.getNodeInst().getConnections(); it.hasNext(); )
			{
				Connection con = it.next();
				if (con.getPortInst() == pi) num++;
			}
			return num;
		}

		public String getExports()
		{
			StringBuffer buf = new StringBuffer();
			for(Iterator<Export> it = pi.getNodeInst().getExports(); it.hasNext(); )
			{
				Export e = it.next();
				if (e.getOriginalPort() != pi) continue;
				if (buf.length() > 0) buf.append(", ");
				buf.append(e.getName());
			}
			return buf.toString();
		}
	}

	/**
	 * Reloads the dialog when Highlights change
	 */
	public void highlightChanged(Highlighter h)
	{
		List<Geometric> geoms = h.getHighlightedEObjs(true, false);
		if (geoms.size() != 1)
		{
			portTable.loadPorts(null);
		} else
		{
			NodeInst ni = (NodeInst)geoms.get(0);
			portTable.loadPorts(ni);
		}
	}

	/**
	 * Called when by a Highlighter when it loses focus. The argument
	 * is the Highlighter that has gained focus (may be null).
	 * @param highlighterGainedFocus the highlighter for the current window (may be null).
	 */
	public void highlighterLostFocus(Highlighter highlighterGainedFocus) {}

    /**
     * Respond to database changes we care about
     * @param e database change event
     */
    public void databaseChanged(DatabaseChangeEvent e)
    {
        // reload if the current node changed
    	boolean changed = false;
		if (e.objectChanged(currentNode)) changed = true;
		for(Export ex : exportsOnNode) if (e.objectChanged(ex)) changed = true;
		if (changed)
        {
            // update dialog
			portTable.loadPorts(currentNode);
        }
    }

	protected void escapePressed() { doneActionPerformed(null); }

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        done = new javax.swing.JButton();
        title = new javax.swing.JLabel();
        portPane = new javax.swing.JScrollPane();
        selectAll = new javax.swing.JButton();
        deselectAll = new javax.swing.JButton();
        reExportPorts = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        deleteExports = new javax.swing.JButton();
        showPorts = new javax.swing.JButton();
        toggleSelection = new javax.swing.JButton();
        highlightPorts = new javax.swing.JButton();

        setTitle("Manipulate Ports");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        done.setText("Done");
        done.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                doneActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(done, gridBagConstraints);

        title.setText("Ports on node");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(title, gridBagConstraints);

        portPane.setMinimumSize(new java.awt.Dimension(200, 200));
        portPane.setPreferredSize(new java.awt.Dimension(200, 200));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(portPane, gridBagConstraints);

        selectAll.setText("Check All Ports");
        selectAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectAllActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(selectAll, gridBagConstraints);

        deselectAll.setText("Uncheck All Ports");
        deselectAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deselectAllActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(deselectAll, gridBagConstraints);

        reExportPorts.setText("ReExport Checked Ports");
        reExportPorts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reExportPortsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(reExportPorts, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(jSeparator1, gridBagConstraints);

        deleteExports.setText("Delect Exports on Checked Ports");
        deleteExports.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteExportsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(deleteExports, gridBagConstraints);

        showPorts.setText("Show Checked Ports");
        showPorts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showPortsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(showPorts, gridBagConstraints);

        toggleSelection.setText("Toggle Check");
        toggleSelection.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleSelectionActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(toggleSelection, gridBagConstraints);

        highlightPorts.setText("Highlight Checked Ports");
        highlightPorts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highlightPortsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(highlightPorts, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void toggleSelectionActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_toggleSelectionActionPerformed
	{//GEN-HEADEREND:event_toggleSelectionActionPerformed
		portTable.toggleChecks();
	}//GEN-LAST:event_toggleSelectionActionPerformed

	private void showPortsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showPortsActionPerformed
	{//GEN-HEADEREND:event_showPortsActionPerformed
		portTable.showChecked();
	}//GEN-LAST:event_showPortsActionPerformed

	private void deleteExportsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteExportsActionPerformed
	{//GEN-HEADEREND:event_deleteExportsActionPerformed
		portTable.unExportChecked();
	}//GEN-LAST:event_deleteExportsActionPerformed

	private void reExportPortsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_reExportPortsActionPerformed
	{//GEN-HEADEREND:event_reExportPortsActionPerformed
		portTable.reExportChecked();
	}//GEN-LAST:event_reExportPortsActionPerformed

	private void deselectAllActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deselectAllActionPerformed
	{//GEN-HEADEREND:event_deselectAllActionPerformed
		portTable.uncheckAll();
	}//GEN-LAST:event_deselectAllActionPerformed

	private void selectAllActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectAllActionPerformed
	{//GEN-HEADEREND:event_selectAllActionPerformed
		portTable.checkAll();
	}//GEN-LAST:event_selectAllActionPerformed

	private void doneActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_doneActionPerformed
	{//GEN-HEADEREND:event_doneActionPerformed
		closeDialog(null);
	}//GEN-LAST:event_doneActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		JTableHeader header = portTable.getTableHeader();
		header.removeMouseListener(columnListener);
	    UserInterfaceMain.removeDatabaseChangeListener(this);
		Highlighter.removeHighlightListener(this);
		dispose();
	}//GEN-LAST:event_closeDialog

    private void highlightPortsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highlightPortsActionPerformed
		portTable.highlightChecked();
    }//GEN-LAST:event_highlightPortsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteExports;
    private javax.swing.JButton deselectAll;
    private javax.swing.JButton done;
    private javax.swing.JButton highlightPorts;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JScrollPane portPane;
    private javax.swing.JButton reExportPorts;
    private javax.swing.JButton selectAll;
    private javax.swing.JButton showPorts;
    private javax.swing.JLabel title;
    private javax.swing.JButton toggleSelection;
    // End of variables declaration//GEN-END:variables
}
