/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SelectObject.java
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

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.KeyBindingManager;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.menus.FileMenu;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowContent;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.TextUtils;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.ActionMap;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


/**
 * Class to handle the "Select Object" dialog.
 */
public class SelectObject extends EModelessDialog implements DatabaseChangeListener
{
    private static SelectObject theDialog = null;
	private static final int NODES   = 1;
	private static final int ARCS    = 2;
	private static final int EXPORTS = 3;
	private static final int NETS    = 4;
	private static int what = NODES;
	private Cell cell;
	private JList list;
	private DefaultListModel model;
	private List<Object> associatedThings;

	public static void selectObjectDialog(Cell thisCell, boolean updateOnlyIfVisible)
	{
        if (theDialog == null)
        {
            if (updateOnlyIfVisible) return; // it is not previously open
            JFrame jf = null;
            if (TopLevel.isMDIMode()) jf = TopLevel.getCurrentJFrame();
            theDialog = new SelectObject(jf);
        }
        if (updateOnlyIfVisible && !theDialog.isVisible()) return; // it is not previously visible
		theDialog.setVisible(true);
		theDialog.buttonClicked(thisCell);
		theDialog.toFront();
	}

	/** Creates new form SelectObject */
	private SelectObject(Frame parent)
	{
		super(parent);
		initComponents();
		getRootPane().setDefaultButton(done);
		UserInterfaceMain.addDatabaseChangeListener(this);

        switch (what)
		{
			case NODES:   nodes.setSelected(true);      break;
			case ARCS:    arcs.setSelected(true);       break;
			case EXPORTS: exports.setSelected(true);    break;
			case NETS:    networks.setSelected(true);   break;
		}

		model = new DefaultListModel();
		list = new JList(model);
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		objectPane.setViewportView(list);
		associatedThings = new ArrayList<Object>();
		list.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent evt) { listClicked(); }
		});

		done.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { closeDialog(null); }
		});

		nodes.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { buttonClicked(null); }
		});
		arcs.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { buttonClicked(null); }
		});
		exports.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { buttonClicked(null); }
		});
		networks.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { buttonClicked(null); }
		});

		searchText.getDocument().addDocumentListener(new SelecdtObjectDocumentListener(this));

		// special case for this dialog: allow Electric quick-keys to pass-through
        TopLevel top = TopLevel.getCurrentJFrame();
        if (top != null && top.getTheMenuBar() != null)
        {
        	KeyBindingManager.KeyMaps km = top.getEMenuBar().getKeyMaps();
        	InputMap im = km.getInputMap();
        	ActionMap am = km.getActionMap();
    		getRootPane().getInputMap().setParent(im);
    		getRootPane().getActionMap().setParent(am);
    		findText.getInputMap().setParent(im);
    		findText.getActionMap().setParent(am);
    		list.getInputMap().setParent(im);
    		list.getActionMap().setParent(am);
        }
		finishInitialization();
	}

	protected void escapePressed() { closeDialog(null); }

	/**
	 * Respond to database changes and reload the list.
	 * @param e database change event
	 */
	public void databaseChanged(DatabaseChangeEvent e)
	{
		if (!isVisible()) return;
		buttonClicked(null);
	}

	private void listClicked()
	{
        if (cell == null)
        {
			System.out.println("There is no current cell for this operation.");
			return;
        }
		Netlist netlist = cell.getNetlist();
		if (netlist == null)
		{
			System.out.println("Sorry, a deadlock aborted selection (network information unavailable).  Please try again");
			return;
		}
        int [] si = list.getSelectedIndices();
		if (si.length <= 0) return;
		boolean unselected = true;
		Set<Highlighter> finishThese = new HashSet<Highlighter>();
		for(Iterator<WindowFrame> wIt = WindowFrame.getWindows(); wIt.hasNext(); )
		{
			WindowFrame wf = wIt.next();
	        WindowContent wc = wf.getContent();
	        if (wc == null) continue;
	        if (wc.getCell() != cell) continue;
	        Highlighter highlighter = wc.getHighlighter();
	        if (highlighter == null) continue;

	        if (unselected)
	        {
	        	unselected = false;
				highlighter.clear();
		        for(int i=0; i<si.length; i++)
				{
					int index = si[i];
					Object obj = associatedThings.get(index);
					if (nodes.isSelected())
					{
						// find nodes
						NodeInst ni = (NodeInst)obj;
						highlighter.addElectricObject(ni, cell);
					} else if (arcs.isSelected())
					{
						// find arcs
						ArcInst ai = (ArcInst)obj;
						highlighter.addElectricObject(ai, cell);
					} else if (exports.isSelected())
					{
						// find exports
						Export pp = (Export)obj;
						highlighter.addText(pp, cell, Export.EXPORT_NAME);
					} else
					{
						// highlight selected network
						Network net = (Network)obj;
						highlighter.addNetwork(net, cell);
					}
				}
			}
			highlighter.ensureHighlightingSeen(wf);
			finishThese.add(highlighter);
		}
		for(Highlighter highlighter : finishThese)
			highlighter.finished();
	}

	/**
	 * Class to package an object with its name.
	 */
	private static class Pair implements Comparable<Pair>
	{
		String pairName;
		Object pairObj;

		Pair(String name, Object obj)
		{
			pairName = name;
			pairObj = obj;
		}

		public int compareTo(Pair o)
		{
			return TextUtils.STRING_NUMBER_ORDER.compare(pairName, o.pairName);
		}
	}

	/**
     * Method to load the dialog depending on cell selected.
     * It is not getCurrentCell because of down/up hierarchy calls.
     * @param thisCell
     */
	private void buttonClicked(Cell thisCell)
	{
		model.clear();
		associatedThings.clear();
		cell = (thisCell != null) ? thisCell: WindowFrame.getCurrentCell();
		if (cell == null) return;

		List<Pair> allNames = new ArrayList<Pair>();
		if (nodes.isSelected())
		{
			// show all nodes
			what = NODES;
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				allNames.add(new Pair(ni.getName(), ni));
			}
		} else if (arcs.isSelected())
		{
			// show all arcs
			what = ARCS;
			for(Iterator<ArcInst> it = cell.getArcs(); it.hasNext(); )
			{
				ArcInst ai = it.next();
				allNames.add(new Pair(ai.getName(), ai));
			}
		} else if (exports.isSelected())
		{
			// show all exports
			what = EXPORTS;
			for(Iterator<PortProto> it = cell.getPorts(); it.hasNext(); )
			{
				Export pp = (Export)it.next();
				allNames.add(new Pair(pp.getName(), pp));
			}
		} else
		{
			// show all networks
			what = NETS;
			Netlist netlist = cell.getNetlist();
			if (netlist == null)
			{
				System.out.println("Sorry, a deadlock aborted selection (network information unavailable).  Please try again");
				return;
			}
			for(Iterator<Network> it = netlist.getNetworks(); it.hasNext(); )
			{
				Network net = it.next();
				String netName = net.describe(false);
				if (netName.length() == 0) continue;
				allNames.add(new Pair(netName, net));
			}
		}
		Collections.sort(allNames);
		for(Pair p: allNames)
		{
			model.addElement(p.pairName);
			associatedThings.add(p.pairObj);
		}
	}

	private void searchTextChanged(String currentSearchText, int limit)
	{
		if (currentSearchText.length() == 0) return;
		List<Integer> selected = new ArrayList<Integer>();
        int flags = Pattern.CASE_INSENSITIVE+Pattern.UNICODE_CASE;
        if (regExp.isSelected())
        {
        	// try regular expression selection
	        Pattern p = null;
	        try
	        {
	        	p = Pattern.compile(currentSearchText, flags);
	    		if (currentSearchText.length() == 0) return;
	    		for(int i=0; i<model.size(); i++)
	    		{
	    			String s = (String)model.get(i);
	                Matcher m = p.matcher(s);
	                if (m.find())
	    				selected.add(new Integer(i));
	    		}
	        } catch (PatternSyntaxException e)
	        {
	        	System.out.println("Invalid regular expression, using straight search");
	        }
        }

        // non-regular expressions: just do straight search
		for(int i=0; i<model.size(); i++)
		{
			String s = (String)model.get(i);
            if (s.indexOf(currentSearchText) >= 0)
				selected.add(new Integer(i));
		}
		
		// 1000 is an arbitrary number at this point.
		if (selected.size() > limit)
		{
			if (limit == 1)
			{
				while (selected.size() > 1) selected.remove(selected.size()-1);
			} else
			{
				String msg = selected.size() + " items selected. Please confirm potential time consuming display operation";
				String[] options = new String[] {"Display", "Cancel"};
			
	            int ret = FileMenu.showFileMenuOptionDialog(TopLevel.getCurrentJFrame(), msg,
	                    "Show Large Number of Items", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
	                    null, options, options[0], null);
	            if (ret != 0)
	            	return; // skipping operation
			}
		}

		if (selected.size() > 0)
		{
			int [] indices = new int[selected.size()];
			int i = 0;
			for(Integer iO: selected)
				indices[i++] = iO.intValue();
			list.setSelectedIndices(indices);
			if (limit == 1) list.ensureIndexIsVisible(selected.get(0));
		}
		listClicked();
	}

	/**
	 * Class to handle changes to the search text field.
	 */
	private class SelecdtObjectDocumentListener implements DocumentListener
	{
		SelectObject dialog;

		SelecdtObjectDocumentListener(SelectObject dialog)
		{
			this.dialog = dialog;
		}

		public void changedUpdate(DocumentEvent e) { dialog.searchTextChanged(searchText.getText(), 1); }
		public void insertUpdate(DocumentEvent e) { dialog.searchTextChanged(searchText.getText(), 1); }
		public void removeUpdate(DocumentEvent e) { dialog.searchTextChanged(searchText.getText(), 1); }
	}

	/** This method is called from within the constructor to
	 * initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is
	 * always regenerated by the Form Editor.
	 */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        whatGroup = new javax.swing.ButtonGroup();
        done = new javax.swing.JButton();
        objectPane = new javax.swing.JScrollPane();
        jLabel1 = new javax.swing.JLabel();
        searchText = new javax.swing.JTextField();
        findText = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        nodes = new javax.swing.JRadioButton();
        exports = new javax.swing.JRadioButton();
        arcs = new javax.swing.JRadioButton();
        networks = new javax.swing.JRadioButton();
        regExp = new javax.swing.JCheckBox();

        setTitle("Select Object");
        setName(""); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                closeDialog(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        done.setText("Done");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(done, gridBagConstraints);

        objectPane.setMinimumSize(new java.awt.Dimension(200, 200));
        objectPane.setPreferredSize(new java.awt.Dimension(200, 200));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        getContentPane().add(objectPane, gridBagConstraints);

        jLabel1.setText("Search:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
        getContentPane().add(jLabel1, gridBagConstraints);

        searchText.setColumns(8);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
        getContentPane().add(searchText, gridBagConstraints);

        findText.setText("Find");
        findText.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                findTextActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
        getContentPane().add(findText, gridBagConstraints);

        jPanel1.setLayout(new java.awt.GridBagLayout());

        whatGroup.add(nodes);
        nodes.setText("Nodes");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(nodes, gridBagConstraints);

        whatGroup.add(exports);
        exports.setText("Exports");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(exports, gridBagConstraints);

        whatGroup.add(arcs);
        arcs.setText("Arcs");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(arcs, gridBagConstraints);

        whatGroup.add(networks);
        networks.setText("Networks");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(4, 4, 4, 4);
        jPanel1.add(networks, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        getContentPane().add(jPanel1, gridBagConstraints);

        regExp.setText("Use regular expressions");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 4);
        getContentPane().add(regExp, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void findTextActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_findTextActionPerformed
	{//GEN-HEADEREND:event_findTextActionPerformed
		String search = searchText.getText();
		searchTextChanged(search, 1000);
	}//GEN-LAST:event_findTextActionPerformed

	/** Closes the dialog */
	private void closeDialog(java.awt.event.WindowEvent evt)//GEN-FIRST:event_closeDialog
	{
		setVisible(false);
		dispose();
	}//GEN-LAST:event_closeDialog

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton arcs;
    private javax.swing.JButton done;
    private javax.swing.JRadioButton exports;
    private javax.swing.JButton findText;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JRadioButton networks;
    private javax.swing.JRadioButton nodes;
    private javax.swing.JScrollPane objectPane;
    private javax.swing.JCheckBox regExp;
    private javax.swing.JTextField searchText;
    private javax.swing.ButtonGroup whatGroup;
    // End of variables declaration//GEN-END:variables
}
