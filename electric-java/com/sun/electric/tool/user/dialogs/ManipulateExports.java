/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ManipulateExports.java
 *
 * Copyright (c) 2008, Static Free Software. All rights reserved.
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
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.user.ExportChanges;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.TextUtils;

import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

/**
 * Class to handle the "Manipulate Exports" dialog.
 */
public class ManipulateExports extends EDialog implements DatabaseChangeListener
{
	private static final String [] columnNames = {"Check", "Name", "Layer", "Characteristic", "Body Only"};
	private static int sortColumn = 1;
	private static boolean sortAscending = true;

	private ExportsTable exportTable;
	private ColumnListener columnListener;
	private Map<JComboBox,Integer> lastIndices = new HashMap<JComboBox,Integer>();

	public static void showDialog()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new ManipulateExports(TopLevel.getCurrentJFrame(), cell);
	}

	/** Creates new form Manipulate Exports */
	private ManipulateExports(Frame parent, Cell cell)
	{
		super(parent, false);
		initComponents();
		getRootPane().setDefaultButton(done);

		title.setText("Exports in cell " + cell.describe(false));

		exportTable = new ExportsTable(cell);
		JTableHeader header = exportTable.getTableHeader();
		columnListener = new ColumnListener();
		header.addMouseListener(columnListener);
		exportPane.setViewportView(exportTable);
		finishInitialization();
		setVisible(true);
        UserInterfaceMain.addDatabaseChangeListener(this);
	}

	private class ColumnListener extends MouseAdapter
	{
		public void mouseClicked(MouseEvent e)
		{
			TableColumnModel colModel = exportTable.getColumnModel();
			int colNumber = colModel.getColumnIndexAtX(e.getX());
			int modelIndex = colModel.getColumn(colNumber).getModelIndex();
			if (modelIndex < 0) return;
			if (sortColumn == modelIndex) sortAscending = !sortAscending; else
			{
				sortColumn = modelIndex;
				sortAscending = true;
			}
			ExportTableModel model = exportTable.getModel();
			model.sortTable();
			model.fireTableStructureChanged();
		}
	}

	private class ExportsTable extends JTable
	{
		private ExportTableModel model;
		private Cell cell;

		/**
		 * Constructor for ExportsTable
		 */
		public ExportsTable(Cell cell)
		{
			this.cell = cell;
			boolean hasBodyOnly = cell.getView() == View.SCHEMATIC;
			model = new ExportTableModel(cell, hasBodyOnly);
			setModel(model);
			TableColumn tc = getColumn(getColumnName(0));
			if (tc != null) tc.setPreferredWidth(40);
			tc = getColumn(getColumnName(1));
			if (tc != null) tc.setPreferredWidth(120);
			tc = getColumn(getColumnName(2));
			if (tc != null) tc.setPreferredWidth(100);
			tc = getColumn(getColumnName(3));
			if (tc != null)
			{
				tc.setPreferredWidth(80);
				tc.setCellRenderer(new CellComboBoxRenderer());
			}
			if (hasBodyOnly)
			{
				tc = getColumn(getColumnName(4));
				if (tc != null) tc.setPreferredWidth(60);
			}
			loadExportTable();
		}

		public void loadExportTable()
		{
			model.clearAll();
			for(Iterator<Export> it = cell.getExports(); it.hasNext(); )
			{
				Export e = it.next();
				model.newVar(e);
			}
			model.sortTable();
		}

		public TableCellEditor getCellEditor(int row, int col)
		{
			if (col != 3) return super.getCellEditor(row, col);
			ExportEntry ee = model.exports.get(row);
			return ee.dce;
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

		public ExportTableModel getModel() { return model; }

		public void checkAll() { model.checkAll(); }

		public void uncheckAll() { model.uncheckAll(); }

		public void renumberCheckedExports() { model.renumberCheckedExports(); }

		public void unExportChecked() { model.unExportChecked(); }

		public void showChecked() { model.showChecked(); }

		public void highlightChecked() { model.highlightChecked(); }
	}

	/**
	 * Model for storing Table data
	 */
	private class ExportTableModel extends AbstractTableModel
	{
		private Cell cell;
		private List<ExportEntry> exports;
		private boolean hasBodyOnly;

		/**
		 * Class to sort exports.
		 */
		private class ExportEntrySort implements Comparator<ExportEntry>
		{
			public int compare(ExportEntry p1, ExportEntry p2)
			{
				if (!sortAscending)
				{
					ExportEntry swap = p1;
					p1 = p2;
					p2 = swap;
				}
				String s1 = null, s2 = null;
				switch (sortColumn)
				{
					case 0:		// selection
						boolean b1 = p1.isSelected();
						boolean b2 = p2.isSelected();
						if (b1 == b2) return 0;
						if (b1) return 1;
						return -1;
					case 1:		// name
						s1 = p1.name;
						s2 = p2.name;
						break;
					case 2:		// layer
						s1 = getLayer(p1.getExport());
						s2 = getLayer(p2.getExport());
						break;
					case 3:		// characteristics
						s1 = p1.ch.getName();
						s2 = p2.ch.getName();
						return s1.compareTo(s2);
					case 4:		// body-only
						b1 = p1.isBodyOnly();
						b2 = p2.isBodyOnly();
						if (b1 == b2) return 0;
						if (b1) return 1;
						return -1;
				}
				return TextUtils.STRING_NUMBER_ORDER.compare(s1, s2);
			}
		}

		// constructor
		private ExportTableModel(Cell cell, boolean hasBodyOnly)
		{
			this.cell = cell;
			this.hasBodyOnly = hasBodyOnly;
			exports = new ArrayList<ExportEntry>();
		}

		public void clearAll()
		{
			exports.clear();
		}

		/**
		 * Create a new var with default properties
		 */
		public void newVar(Export e)
		{
			ExportEntry ve = new ExportEntry(e, this);
			exports.add(ve);
		}

		public void sortTable()
		{
			Collections.sort(exports, new ExportEntrySort());
			fireTableDataChanged();
		}

		public void checkAll()
		{
			int i = 0;
			for(ExportEntry pe : exports)
			{
				pe.setSelected(true);
				fireTableCellUpdated(i++, 0);
			}
		}

		public void uncheckAll()
		{
			int i = 0;
			for(ExportEntry pe : exports)
			{
				pe.setSelected(false);
				fireTableCellUpdated(i++, 0);
			}
		}

		public void renumberCheckedExports()
		{
			List<Export> queuedExports = new ArrayList<Export>();
			for(ExportEntry pe : exports)
			{
				if (!pe.isSelected()) continue;
				queuedExports.add(pe.getExport());
			}
			new ExportChanges.RenumberNumericExports(queuedExports);
		}

		public void unExportChecked()
		{
			List<Export> queuedExports = new ArrayList<Export>();
			for(ExportEntry pe : exports)
			{
				if (!pe.isSelected()) continue;
				queuedExports.add(pe.getExport());
			}
			ExportChanges.deleteExports(cell, queuedExports);
		}

		public void showChecked()
		{
			List<Export> exportList = new ArrayList<Export>();
			for(ExportEntry pe : exports)
			{
				if (!pe.isSelected()) continue;
				exportList.add(pe.getExport());
			}
			ExportChanges.showExports(exportList);
		}

		public void highlightChecked()
		{
			UserInterface ui = Job.getUserInterface();
			EditWindow_ wnd = ui.getCurrentEditWindow_();
			if (wnd == null) return;
			wnd.clearHighlighting();
			for(ExportEntry pe : exports)
			{
				if (!pe.isSelected()) continue;
				wnd.addHighlightText(pe.getExport(), cell, Export.EXPORT_NAME);
			}
			wnd.finishedHighlighting();
		}

		/** Method to get the number of columns. */
		public int getColumnCount() { return hasBodyOnly ? 5 : 4; }

		/** Method to get the number of rows. */
		public int getRowCount() { return exports.size(); }

		/** Method to get a location in the table. */
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			ExportEntry pe = exports.get(rowIndex);
			if (pe == null) return null;

			switch (columnIndex)
			{
				// selected
				case 0: return Boolean.valueOf(pe.isSelected());

				// name
				case 1: return pe.name;

				// layer
				case 2: return getLayer(pe.getExport());

				// characteristic
				case 3: return pe.cb;

				// body-only
				case 4: return Boolean.valueOf(pe.isBodyOnly());
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
			if (col == 0 || col == 1 || col == 3 || col == 4) return true;
			return false;
		}

		/** Method to set a value. */
		public void setValueAt(Object aValue, int row, int col)
		{
			ExportEntry ve = exports.get(row);
			if (ve == null) return;

			if (col == 0)
			{
				// change the "checked" state of the export
				Boolean b = (Boolean)aValue;
				if (ve.isSelected() != b.booleanValue())
				{
					ve.setSelected(b.booleanValue());
					fireTableCellUpdated(row, col);
				}
			} else if (col == 1)
			{
				// change the name of the export
				ve.name = (String)aValue;
				new ExportChanges.RenameExport(ve.getExport(), ve.name);
			} else if (col == 3)
			{
				// change the characteristics of the export
				ve.ch = (PortCharacteristic)aValue;
				new ExportChanges.ChangeExportCharacteristic(ve.getExport(), ve.ch);
			} else if (col == 4)
			{
				// change the "body-only" state of the export
				Boolean b = (Boolean)aValue;
				if (ve.isBodyOnly() != b.booleanValue())
				{
					ve.setBodyOnly(b.booleanValue());
					new ExportChanges.ChangeExportBodyOnly(ve.getExport(), b.booleanValue());
					fireTableCellUpdated(row, col);
				}
			}
		}

		public Class<?> getColumnClass(int col)
		{
			if (col == 0 || col == 4) return Boolean.class;
			return String.class;
		}

		/**
		 * Convert an Export to the layers on it.
		 * @param e the Export.
		 * @return the name of the layer on the export.
		 */
		private String getLayer(Export e)
		{
			ArcProto [] arcs = e.getBasePort().getConnections();
			String layers = "";
			ArcProto firstGeneric = null;
			for(int i=0; i<arcs.length; i++)
			{
				ArcProto ap = arcs[i];
				if (ap.getTechnology() == Generic.tech())
				{
					if (firstGeneric == null) firstGeneric = ap;
				} else
				{
					if (layers.length() > 0) layers += ", ";
					layers += ap.getLayer(0).getName();
				}
			}
			if (layers.length() == 0 && firstGeneric != null)
				layers = firstGeneric.getLayer(0).getName();
			return layers;
		}
	}

	private class ExportEntry
	{
		private boolean selected, bodyOnly;
		private String name;
		private PortCharacteristic ch;
		private Export e;
		private JComboBox cb;
		private DefaultCellEditor dce;
		private boolean stateChanging = false;

		public ExportEntry(Export e, ExportTableModel etm)
		{
			this.e = e;
			this.name = e.getName();
			this.ch = e.getCharacteristic();
			this.bodyOnly = e.isBodyOnly();

			List<PortCharacteristic> chars = PortCharacteristic.getOrderedCharacteristics();
			PortCharacteristic [] charNames = new PortCharacteristic[chars.size()];
			for(int i=0; i<chars.size(); i++) charNames[i] = chars.get(i);
			this.cb = new JComboBox(charNames);
			cb.addItemListener(new ComboBoxItemListener(cb, etm));
			this.cb.setSelectedItem(ch);
			this.dce = new DefaultCellEditor(cb);
		}

		public Export getExport() { return e; }

		public boolean isSelected() { return selected; }

		public void setSelected(boolean s) { selected = s; }

		public boolean isBodyOnly() { return bodyOnly; }

		public void setBodyOnly(boolean b) { bodyOnly = b; }

		private class ComboBoxItemListener implements ItemListener
		{
			private JComboBox cb;
			private ExportTableModel etm;

			public ComboBoxItemListener(JComboBox cb, ExportTableModel etm)
			{
				this.cb = cb;
				this.etm = etm;
			}

			public void itemStateChanged(ItemEvent evt)
	        {
				if (stateChanging) return;
	        	if (evt.getStateChange() == ItemEvent.SELECTED)
	        	{
	        		Integer lastIndex = lastIndices.get(cb);
	        		if (lastIndex == null) lastIndices.put(cb, lastIndex = new Integer(cb.getSelectedIndex()));
	        		if (lastIndex.intValue() != cb.getSelectedIndex())
	        		{
	        			ExportEntry thisEE = null;
	        			for(ExportEntry ee : etm.exports)
	        			{
	        				if (ee.cb == cb) { thisEE = ee;   break; }
	        			}
	        			if (thisEE != null && thisEE.isSelected())
	        			{
	        				// update all other entries that are also selected
		        			stateChanging = true;
		        			for(ExportEntry ee : etm.exports)
		        			{
		        				if (ee != thisEE && ee.isSelected())
		        					ee.cb.setSelectedIndex(cb.getSelectedIndex());
		        			}
		        			stateChanging = false;
	        			}
	        			etm.fireTableDataChanged();
	        		}
	        		lastIndices.put(cb, new Integer(cb.getSelectedIndex()));
	        	}
	        }
		}
	}

	public class CellComboBoxRenderer extends JComboBox implements TableCellRenderer
	{
		public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column)
		{
			ExportsTable et = (ExportsTable)table;
			ExportTableModel etm = et.getModel();
			JComboBox cb = (JComboBox)etm.getValueAt(row, column);
			return cb;
		}
	}

    /**
     * Respond to database changes we care about
     * @param e database change event
     */
    public void databaseChanged(DatabaseChangeEvent e)
    {
        // reload if any Exports on the cell were changed
        boolean reload = false;
        for(ExportEntry ee : exportTable.model.exports)
        {
			if (e.objectChanged(ee.getExport()))
			{
				reload = true;
				break;
			}
		}
        if (reload)
        {
            // update dialog
            exportTable.loadExportTable();
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
        exportPane = new javax.swing.JScrollPane();
        selectAll = new javax.swing.JButton();
        deselectAll = new javax.swing.JButton();
        reNumberExports = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        deleteExports = new javax.swing.JButton();
        showExports = new javax.swing.JButton();
        toggleSelection = new javax.swing.JButton();
        highlightExports = new javax.swing.JButton();

        setTitle("Manipulate Exports");
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

        title.setText("Exports in Cell");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(title, gridBagConstraints);

        exportPane.setMinimumSize(new java.awt.Dimension(200, 200));
        exportPane.setPreferredSize(new java.awt.Dimension(200, 200));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(exportPane, gridBagConstraints);

        selectAll.setText("Check All Exports");
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

        deselectAll.setText("Uncheck All Exports");
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

        reNumberExports.setText("Renumber Checked Numeric Export Names");
        reNumberExports.setToolTipText("Renames the selected exports so that trailing numbers are in order");
        reNumberExports.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reNumberExportsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(reNumberExports, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        getContentPane().add(jSeparator1, gridBagConstraints);

        deleteExports.setText("Delete Checked Exports");
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

        showExports.setText("Show Checked Exports");
        showExports.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showExportsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(showExports, gridBagConstraints);

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

        highlightExports.setText("Highlight Checked Exports");
        highlightExports.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highlightExportsActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(highlightExports, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void toggleSelectionActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_toggleSelectionActionPerformed
	{//GEN-HEADEREND:event_toggleSelectionActionPerformed
		exportTable.toggleChecks();
	}//GEN-LAST:event_toggleSelectionActionPerformed

    private void showExportsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showExportsActionPerformed
    {//GEN-HEADEREND:event_showExportsActionPerformed
		exportTable.showChecked();
    }//GEN-LAST:event_showExportsActionPerformed

	private void deleteExportsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteExportsActionPerformed
	{//GEN-HEADEREND:event_deleteExportsActionPerformed
		exportTable.unExportChecked();
	}//GEN-LAST:event_deleteExportsActionPerformed

    private void reNumberExportsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_reNumberExportsActionPerformed
    {//GEN-HEADEREND:event_reNumberExportsActionPerformed
		exportTable.renumberCheckedExports();
    }//GEN-LAST:event_reNumberExportsActionPerformed

	private void deselectAllActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deselectAllActionPerformed
	{//GEN-HEADEREND:event_deselectAllActionPerformed
		exportTable.uncheckAll();
	}//GEN-LAST:event_deselectAllActionPerformed

	private void selectAllActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectAllActionPerformed
	{//GEN-HEADEREND:event_selectAllActionPerformed
		exportTable.checkAll();
	}//GEN-LAST:event_selectAllActionPerformed

	private void doneActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_doneActionPerformed
	{//GEN-HEADEREND:event_doneActionPerformed
		closeDialog(null);
	}//GEN-LAST:event_doneActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		JTableHeader header = exportTable.getTableHeader();
		header.removeMouseListener(columnListener);
	    UserInterfaceMain.removeDatabaseChangeListener(this);
		dispose();
	}//GEN-LAST:event_closeDialog

    private void highlightExportsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highlightExportsActionPerformed
        exportTable.highlightChecked();
    }//GEN-LAST:event_highlightExportsActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteExports;
    private javax.swing.JButton deselectAll;
    private javax.swing.JButton done;
    private javax.swing.JScrollPane exportPane;
    private javax.swing.JButton highlightExports;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton reNumberExports;
    private javax.swing.JButton selectAll;
    private javax.swing.JButton showExports;
    private javax.swing.JLabel title;
    private javax.swing.JButton toggleSelection;
    // End of variables declaration//GEN-END:variables
}
