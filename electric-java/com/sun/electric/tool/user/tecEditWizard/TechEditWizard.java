/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TechEditWizard.java
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
package com.sun.electric.tool.user.tecEditWizard;

import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.ui.TopLevel;

import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/**
 * Class to handle the "Technology Creation Wizard" dialog.
 */
public class TechEditWizard extends EDialog
{
	private JSplitPane splitPane;
	private JTree optionTree;
    private TechEditWizardPanel currentOptionPanel;
	private DefaultMutableTreeNode currentDMTN;
	private static TechEditWizardData data = new TechEditWizardData();
	/** The name of the current tab in this dialog. */		private static String currentTabName = "General";

    /**
	 * This method implements the command to show the Technology Creation Wizard dialog.
	 */
	public static void techEditWizardCommand()
	{
		TechEditWizard dialog = new TechEditWizard(TopLevel.getCurrentJFrame());
		dialog.setVisible(true);
	}

	/** Creates new form TechEditWizard */
	public TechEditWizard(Frame parent)
	{
		super(parent, true);
		getContentPane().setLayout(new GridBagLayout());
		setTitle("Technology Creation Wizard");
		setName("");
		addWindowListener(new WindowAdapter()
		{
			public void windowClosing(WindowEvent evt) { closeDialog(evt); }
		});

		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Technology Parameters");
		DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
		optionTree = new JTree(treeModel);
		TreeHandler handler = new TreeHandler(this);
		optionTree.addMouseListener(handler);
		optionTree.addTreeExpansionListener(handler);

		addTreeNode(rootNode, "General");
		addTreeNode(rootNode, "Active");
		addTreeNode(rootNode, "Poly");
		addTreeNode(rootNode, "Gate");
		addTreeNode(rootNode, "Contact");
		addTreeNode(rootNode, "Well/Implant");
		addTreeNode(rootNode, "Metal");
		addTreeNode(rootNode, "Via");
		addTreeNode(rootNode, "Antenna");
		addTreeNode(rootNode, "GDS");

        // pre-expand the tree
		TreePath topPath = optionTree.getPathForRow(0);
		optionTree.expandPath(topPath);
		topPath = optionTree.getPathForRow(1);
		optionTree.expandPath(topPath);

        // searching for selected node
        openSelectedPath(rootNode);

		// the left side of the Technology Editor dialog: a tree
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new GridBagLayout());

		JScrollPane scrolledTree = new JScrollPane(optionTree);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 0;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1.0;   gbc.weighty = 1.0;
		leftPanel.add(scrolledTree, gbc);

		JButton importBut = new JButton("Load Parameters");
		importBut.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { importFromWizardFormatActionPerformed(); }
		});
		gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 1;
		gbc.insets = new Insets(4, 4, 4, 4);
		leftPanel.add(importBut, gbc);

		JButton exportBut = new JButton("Save Parameters");
		exportBut.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { exportActionPerformed(); }
		});
		gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 2;
		gbc.insets = new Insets(4, 4, 4, 4);
		leftPanel.add(exportBut, gbc);

		JButton makeTech = new JButton("Write XML");
		makeTech.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { makeTechnologyActionPerformed(); }
		});
		gbc = new GridBagConstraints();
		gbc.gridx = 1;   gbc.gridy = 1;
		gbc.insets = new Insets(4, 4, 4, 4);
		leftPanel.add(makeTech, gbc);

		JButton okBut = new JButton("Done");
		okBut.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent evt) { okActionPerformed(); }
		});
		gbc = new GridBagConstraints();
		gbc.gridx = 1;   gbc.gridy = 2;
		gbc.insets = new Insets(4, 4, 4, 4);
		leftPanel.add(okBut, gbc);

		getRootPane().setDefaultButton(okBut);

        // build Technology Editor framework
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

		loadOptionPanel();
		recursivelyHighlight(optionTree, rootNode, currentDMTN, optionTree.getPathForRow(0));
		splitPane.setLeftComponent(leftPanel);

		gbc = new GridBagConstraints();
		gbc.gridx = 0;   gbc.gridy = 0;
		gbc.gridwidth = 1;
		gbc.fill = GridBagConstraints.BOTH;
		gbc.weightx = 1.0;   gbc.weighty = 1.0;
		getContentPane().add(splitPane, gbc);

		pack();
		finishInitialization();
	}

	private void addTreeNode(DefaultMutableTreeNode rootNode, String name)
	{
		DefaultMutableTreeNode dmtn = new DefaultMutableTreeNode(name);
		rootNode.add(dmtn);
		if (name.equals(currentTabName))
			currentDMTN = dmtn;
	}

	public TechEditWizardData getTechEditData() { return data; }

    private boolean openSelectedPath(DefaultMutableTreeNode rootNode)
    {
        for (int i = 0; i < rootNode.getChildCount(); i++)
        {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)rootNode.getChildAt(i);
            Object o = node.getUserObject();
            if (o.toString().equals(currentTabName))
            {
                optionTree.scrollPathToVisible(new TreePath(node.getPath()));
                return true;
            }
            if (openSelectedPath(node)) return true;
        }
        return false;
    }

	private void okActionPerformed()
	{
        // gather preference changes on the client
        if (currentOptionPanel != null)
        {
            currentOptionPanel.term();
            currentOptionPanel = null;
        }
        closeDialog(null);
	}

	private void importFromWizardFormatActionPerformed()
	{
		data.importDataFromWizardFormat();
        if (currentOptionPanel != null)
            currentOptionPanel.init();
	}

	private void exportActionPerformed()
	{
        if (currentOptionPanel != null)
            currentOptionPanel.term();
		data.exportData();
	}

	private void makeTechnologyActionPerformed()
	{
        if (currentOptionPanel != null)
            currentOptionPanel.term();
		data.writeXML(true);
	}

	private void loadOptionPanel()
	{
		TechEditWizardPanel ti = createOptionPanel(isModal());
        if (ti == null) return;
        if (currentOptionPanel != null)
            currentOptionPanel.term();
        currentOptionPanel = ti;
        ti.init();
        splitPane.setRightComponent(ti.getComponent());
	}

    private TechEditWizardPanel createOptionPanel(boolean modal)
    {
        if (currentTabName.equals("Active"))
            return new Active(this, modal);
        if (currentTabName.equals("General"))
            return new General(this, modal);
        if (currentTabName.equals("Gate"))
            return new Gate(this, modal);
        if (currentTabName.equals("Poly"))
            return new Poly(this, modal);
        if (currentTabName.equals("Contact"))
            return new Contact(this, modal);
        if (currentTabName.equals("Well/Implant"))
            return new WellImplant(this, modal);
        if (currentTabName.equals("Metal"))
            return new Metal(this, modal);
        if (currentTabName.equals("Via"))
            return new Via(this, modal);
        if (currentTabName.equals("Antenna"))
            return new Antenna(this, modal);
        if (currentTabName.equals("GDS"))
            return new GDS(this, modal);
        return null;
    }

	protected void escapePressed() { okActionPerformed(); }

	private static class TreeHandler implements MouseListener, TreeExpansionListener
	{
		private TechEditWizard dialog;

		TreeHandler(TechEditWizard dialog) { this.dialog = dialog; }

		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}

		public void mousePressed(MouseEvent e)
		{
			TreePath currentPath = dialog.optionTree.getPathForLocation(e.getX(), e.getY());
			if (currentPath == null) return;
			dialog.optionTree.setSelectionPath(currentPath);
			DefaultMutableTreeNode node = (DefaultMutableTreeNode)currentPath.getLastPathComponent();
			currentTabName = (String)node.getUserObject();
			dialog.optionTree.expandPath(currentPath);
			if (!currentTabName.endsWith(" "))
			{
				dialog.loadOptionPanel();
			}
			dialog.pack();
		}

		public void treeCollapsed(TreeExpansionEvent e)
		{
			dialog.pack();
		}
		public void treeExpanded(TreeExpansionEvent e)
		{
			TreePath tp = e.getPath();
			if (tp.getPathCount() == 2)
			{
				// opened a path down to the bottom: close all others
				TreePath topPath = dialog.optionTree.getPathForRow(0);
				DefaultMutableTreeNode node = (DefaultMutableTreeNode)topPath.getLastPathComponent();
				int numChildren = node.getChildCount();
				for(int i=0; i<numChildren; i++)
				{
					DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
					TreePath descentPath = topPath.pathByAddingChild(child);
					if (!descentPath.getLastPathComponent().equals(tp.getLastPathComponent()))
					{
						dialog.optionTree.collapsePath(descentPath);
					}
				}
			}
			dialog.pack();
		}
	}

	/** Closes the dialog */
	private void closeDialog(WindowEvent evt)
	{
		setVisible(false);
		dispose();
	}
}
