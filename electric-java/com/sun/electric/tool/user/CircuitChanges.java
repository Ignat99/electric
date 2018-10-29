/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CircuitChanges.java
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
package com.sun.electric.tool.user;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.database.variable.Variable.Key;
import com.sun.electric.lib.LibFile;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.project.Project;
import com.sun.electric.tool.user.dialogs.EDialog;
import com.sun.electric.tool.user.menus.MenuCommands;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.tool.user.ui.MessagesWindow;
import com.sun.electric.tool.user.ui.OutlineListener;
import com.sun.electric.tool.user.ui.ToolBar;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowContent;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.tool.user.waveform.WaveformWindow;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.EDimension;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

/**
 * Class for user-level changes to the circuit.
 */
public class CircuitChanges
{
	// constructor, never used
	private CircuitChanges() {}

	/****************************** NODE TRANSFORMATION ******************************/

	private static double lastRotationAmount = 90;

    /**
	 * Method to handle the command to rotate the selected objects by an amount.
	 * @param amount the amount to rotate.  If the amount is zero, prompt for an amount.
	 */
	public static void rotateObjects(int amount)
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;

		// if zero rotation, prompt for amount
		if (amount == 0)
		{
			String val = JOptionPane.showInputDialog("Amount to rotate", new Double(lastRotationAmount));
			if (val == null) return;
			double fAmount = TextUtils.atof(val);
			if (fAmount == 0)
			{
				System.out.println("Null rotation amount");
				return;
			}
			lastRotationAmount = fAmount;
			amount = (int)(fAmount * 10);
		}

		List<Geometric> highs = new ArrayList<Geometric>();
		List<ElectricObject> highTexts = new ArrayList<ElectricObject>();
		List<Key> highTextKeys = new ArrayList<Key>();
		
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf != null)
		{
			Highlighter highlighter = wf.getContent().getHighlighter();
			if (highlighter != null)
			{
				for(Highlight high : highlighter.getHighlights())
				{
	                ElectricObject eobj = high.getElectricObject();
                	if (eobj instanceof PortInst) eobj = ((PortInst)eobj).getNodeInst();
	                if (high.isHighlightEOBJ())
	                {
	                	if (eobj instanceof Geometric) 
	                		highs.add((Geometric)eobj);
	                } else if (high.isHighlightText())
	                {
                		highTexts.add(eobj);
                		highTextKeys.add(high.getVarKey());
					}
				}
			}
		}
		if (highs.size() == 0)
		{
			if (highTexts.size() > 0)
			{
				// just rotate text
				new CircuitChangeJobs.RotateText(cell, highTexts, highTextKeys, amount, false, false);
				return;
			}
			System.out.println("Cannot rotate: nothing is selected");
			return;
		}
		new CircuitChangeJobs.RotateSelected(cell, highs, amount, false, false);
	}

	/**
	 * Method to handle the command to mirror the selected objects.
	 * @param horizontally true to mirror horizontally (about the horizontal, flipping the Y value).
	 * False to mirror vertically (about the vertical, flipping the X value).
	 */
	public static void mirrorObjects(boolean horizontally)
	{
        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf == null) return;
        Cell cell = wf.getContent().getCell();

		if (cell == null) return;

		List<Geometric> highs = new ArrayList<Geometric>();
		List<ElectricObject> highTexts = new ArrayList<ElectricObject>();
		List<Key> highTextKeys = new ArrayList<Key>();

        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter != null)
			{
				for(Highlight high : highlighter.getHighlights())
                    {
                        ElectricObject eobj = high.getElectricObject();
                        if (eobj instanceof PortInst) eobj = ((PortInst)eobj).getNodeInst();
                        if (high.isHighlightEOBJ())
                            {
                                if (eobj instanceof Geometric) 
                                    highs.add((Geometric)eobj);
                            } else if (high.isHighlightText())
                            {
                                highTexts.add(eobj);
                                highTextKeys.add(high.getVarKey());
                            }
                    }
			}
		if (highs.size() == 0)
		{
			if (highTexts.size() > 0)
			{
				// just rotate text
				new CircuitChangeJobs.RotateText(cell, highTexts, highTextKeys, 0, true, horizontally);
				return;
			}
			System.out.println("Cannot mirror: nothing is selected");
			return;
		}
		new CircuitChangeJobs.RotateSelected(cell, highs, 0, true, horizontally);
	}

	/****************************** NODE ALIGNMENT ******************************/

	/**
	 * Method to align the selected objects to the grid.
	 */
	public static void alignToGrid()
	{
		// get a list of all selected nodes and arcs
		List<Geometric> selected = MenuCommands.getSelectedObjects(true, true);

		// make a set of selected nodes
		Set<NodeInst> selectedNodes = new HashSet<NodeInst>();
		for(Geometric geom : selected)
		{
			if (geom instanceof NodeInst) selectedNodes.add((NodeInst)geom);
		}

		// make a list of nodes at the ends of arcs that should be added to the list
		List<NodeInst> addedNodes = new ArrayList<NodeInst>();
		for(Geometric geom : selected)
		{
			if (!(geom instanceof ArcInst)) continue;
			ArcInst ai = (ArcInst)geom;
			NodeInst head = ai.getHead().getPortInst().getNodeInst();
			if (!selectedNodes.contains(head))
			{
				addedNodes.add(head);
				selectedNodes.add(head);
			}
			NodeInst tail = ai.getTail().getPortInst().getNodeInst();
			if (!selectedNodes.contains(tail))
			{
				addedNodes.add(tail);
				selectedNodes.add(tail);
			}
		}
		for(NodeInst ni : addedNodes)
			selected.add(ni);

		if (selected.size() == 0)
		{
			System.out.println("Must select something before aligning it to the grid");
			return;
		}
		EDimension alignment = User.getAlignmentToGrid();
		if (alignment.getWidth() <= 0 || alignment.getHeight() <= 0)
		{
			System.out.println("No alignment given: set Alignment Options first");
			return;
		}

		// now align them
		new CircuitChangeJobs.AlignObjects(selected, alignment);
	}

	/**
	 * Method to align the selected nodes.
	 * @param horizontal true to align them horizontally; false for vertically.
	 * @param direction if horizontal is true, meaning is 0 for left, 1 for right, 2 for center.
	 * If horizontal is false, meaning is 0 for top, 1 for bottom, 2 for center.
	 */
	public static void alignNodes(boolean horizontal, int direction)
	{
		// make sure there is a current cell
		Cell np = WindowFrame.needCurCell();
		if (np == null) return;

		// get the objects to be moved (mark nodes with nonzero "temp1")
		List<Geometric> list = MenuCommands.getSelectedObjects(true, true);
		if (list.size() == 0)
		{
			System.out.println("First select objects to move");
			return;
		}

		// make sure they are all in the same cell
		for(Geometric geom : list)
		{
			if (geom.getParent() != np)
			{
				System.out.println("All moved objects must be in the same cell");
				return;
			}
		}

		// count the number of nodes
		List<NodeInst> nodes = new ArrayList<NodeInst>();
		for(Geometric geom : list)
		{
			if (geom instanceof NodeInst) nodes.add((NodeInst)geom);
		}
		int total = nodes.size();
		if (total == 0) return;

		NodeInst [] nis = new NodeInst[total];
		double [] dCX = new double[total];
		double [] dCY = new double[total];
		for(int i=0; i<total; i++)
		{
			nis[i] = nodes.get(i);
		}

		// get bounds
		double lX = 0, hX = 0, lY = 0, hY = 0;
		for(int i=0; i<total; i++)
		{
			NodeInst ni = nis[i];
			Rectangle2D bounds = ni.getBounds();
			if (i == 0)
			{
				lX = bounds.getMinX();
				hX = bounds.getMaxX();
				lY = bounds.getMinY();
				hY = bounds.getMaxY();
			} else
			{
				if (bounds.getMinX() < lX) lX = bounds.getMinX();
				if (bounds.getMaxX() > hX) hX = bounds.getMaxX();
				if (bounds.getMinY() < lY) lY = bounds.getMinY();
				if (bounds.getMaxY() > hY) hY = bounds.getMaxY();
			}
		}

		// determine motion
		for(int i=0; i<total; i++)
		{
			NodeInst ni = nis[i];
			Rectangle2D bounds = ni.getBounds();
			dCX[i] = dCY[i] = 0;
			if (horizontal)
			{
				// horizontal alignment
				switch (direction)
				{
					case 0:		// align to left
						dCX[i] = lX - bounds.getMinX();
						break;
					case 1:		// align to right
						dCX[i] = hX - bounds.getMaxX();
						break;
					case 2:		// align to center
						dCX[i] = (lX + hX) / 2 - bounds.getCenterX();
						break;
				}
			} else
			{
				// vertical alignment
				switch (direction)
				{
					case 0:		// align to top
						dCY[i] = hY - bounds.getMaxY();
						break;
					case 1:		// align to bottom
						dCY[i] = lY - bounds.getMinY();
						break;
					case 2:		// align to center
						dCY[i] = (lY + hY) / 2 - bounds.getCenterY();
						break;
				}
			}
		}
		new CircuitChangeJobs.AlignNodes(nis, dCX, dCY);
	}

	/****************************** ARC MODIFICATION ******************************/

	/**
	 * This method sets the highlighted arcs to Rigid
	 */
	public static void arcRigidCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ChangeArcProperties(cell, CircuitChangeJobs.ChangeArcEnum.RIGID, getHighlighted());
	}

	/**
	 * This method sets the highlighted arcs to Non-Rigid
	 */
	public static void arcNotRigidCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ChangeArcProperties(cell, CircuitChangeJobs.ChangeArcEnum.NONRIGID, getHighlighted());
	}

	/**
	 * This method sets the highlighted arcs to Fixed-Angle
	 */
	public static void arcFixedAngleCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ChangeArcProperties(cell, CircuitChangeJobs.ChangeArcEnum.FIXEDANGLE, getHighlighted());
	}

	/**
	 * This method sets the highlighted arcs to Not-Fixed-Angle
	 */
	public static void arcNotFixedAngleCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ChangeArcProperties(cell, CircuitChangeJobs.ChangeArcEnum.NONFIXEDANGLE, getHighlighted());
	}

	/**
	 * This method toggles the directionality of highlighted arcs.
	 */
	public static void arcDirectionalCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ChangeArcProperties(cell, CircuitChangeJobs.ChangeArcEnum.DIRECTIONAL, getHighlighted());
	}

	/**
	 * This method sets the highlighted arcs to have their head end extended.
	 */
	public static void arcHeadExtendCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ChangeArcProperties(cell, CircuitChangeJobs.ChangeArcEnum.HEADEXTEND, getHighlighted());
	}

	/**
	 * This method sets the highlighted arcs to have their tail end extended.
	 */
	public static void arcTailExtendCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ChangeArcProperties(cell, CircuitChangeJobs.ChangeArcEnum.TAILEXTEND, getHighlighted());
	}

	/**
	 * This method sets the highlighted ports to be negated.
	 */
	public static void toggleNegatedCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ToggleNegationJob(cell, getHighlighted());
	}

    /**
     * Get list of Highlights in current highlighter
     * @return list of Highlights
     */
    public static List<Highlight> getHighlighted()
    {
        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf == null) return new ArrayList<Highlight>();
        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter == null) return new ArrayList<Highlight>();
        return highlighter.getHighlights();
    }

    /**
	 * Method to rip the currently selected bus arc out into individual wires.
	 */
	public static void ripBus()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		List<ArcInst> list = MenuCommands.getSelectedArcs();
		if (list.size() == 0)
		{
			System.out.println("Must select bus arcs to rip into individual signals");
			return;
		}
		new CircuitChangeJobs.RipTheBus(cell, list);
	}

	/****************************** DELETE SELECTED OBJECTS ******************************/

	/**
	 * Method to delete all selected objects.
	 */
	public static void deleteSelected()
	{
        // is this the messages window?
        MessagesWindow mw = MessagesWindow.getFocusOwner();
        if (mw != null) {
            mw.clear(false);
            return;
        }

        // see what type of window is selected
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf == null) return;
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter == null) return;

		// for waveform windows, delete selected signals
		if (wf.getContent() instanceof WaveformWindow)
		{
			WaveformWindow ww = (WaveformWindow)wf.getContent();
			ww.deleteSelectedSignals();
			return;
		}

		// for edit windows doing outline editing, delete the selected point
        if (WindowFrame.getListener() == OutlineListener.theOne)
        {
        	OutlineListener.theOne.deletePoint();
        	return;
        }

        // see if a highlighted area is given
        boolean highlightedArea = ToolBar.getSelectMode() == ToolBar.SelectMode.AREA;
        List<Highlight> highlights = highlighter.getHighlights();
        if (highlights.size() == 1)
        {
        	Highlight high = highlights.get(0);
        	if (high instanceof HighlightArea) highlightedArea = true;
        }

        if (highlightedArea)
		{
            EditWindow wnd = EditWindow.getCurrent();
            Rectangle2D bounds = highlighter.getHighlightedArea(wnd);
			if (bounds == null)
			{
				System.out.println("Nothing is selected");
				return;
			}
			new CircuitChangeJobs.DeleteSelectedGeometry(cell, bounds);
		} else
		{
			// disable the "node moves with text" because it causes nodes to be deleted with text, too
			boolean formerMoveWithText = User.isMoveNodeWithExport();
			Pref.delayPrefFlushing();
			User.setMoveNodeWithExport(false);

			// get what is highlighted
			List<DisplayedText> highlightedText = highlighter.getHighlightedText(true);
            List<Geometric> highlighted = highlighter.getHighlightedEObjs(true, true);

            // restore "node moves with text"
            User.setMoveNodeWithExport(formerMoveWithText);
			Pref.resumePrefFlushing();

			// delete if anything was selected
			if (highlightedText.size() == 0 && highlighted.size() == 0) return;
	        new CircuitChangeJobs.DeleteSelected(cell, highlightedText, highlighted, User.isReconstructArcsAndExportsToDeletedCells());
		}
	}

    public static void cellCenterToCenterOfSelection() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;

        Cell cell = WindowFrame.needCurCell();
        if (cell == null) return;
		if (CircuitChangeJobs.cantEdit(cell, null, true, false, false) != 0) return;

        Highlighter highlighter = wnd.getHighlighter();
        if (highlighter == null) return;
		Rectangle2D bounds = highlighter.getHighlightedArea(wnd);
		if (bounds == null) return;
        new CircuitChangeJobs.CellCenterToCenterOfSelection(cell, EPoint.fromLambda(bounds.getCenterX(), bounds.getCenterY()));
    }

	/**
	 * Method to delete arcs connected to selected nodes.
	 * @param both true if both ends of the arc must be selected.
	 */
	public static void deleteArcsOnSelected(boolean both)
	{
		// get the selection
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;

		// make sure the cell is editable
        Cell cell = WindowFrame.needCurCell();
        if (cell == null) return;
		if (CircuitChangeJobs.cantEdit(cell, null, true, false, false) != 0) return;

		// make a set of selected nodes
        Highlighter highlighter = wnd.getHighlighter();
        if (highlighter == null) return;
        Set<NodeInst> selectedNodes = new HashSet<NodeInst>();
        for(Geometric g : highlighter.getHighlightedEObjs(true, false))
        	selectedNodes.add((NodeInst)g);

        // make a set of arcs to delete
        Set<ArcInst> arcsToDelete = new HashSet<ArcInst>();
        for(NodeInst ni : selectedNodes)
        {
        	for(Iterator<Connection> it = ni.getConnections(); it.hasNext(); )
        	{
        		Connection con = it.next();
        		ArcInst ai = con.getArc();
        		if (both)
        		{
        			if (!selectedNodes.contains(ai.getHeadPortInst().getNodeInst())) continue;
        			if (!selectedNodes.contains(ai.getTailPortInst().getNodeInst())) continue;
        		}
        		arcsToDelete.add(ai);
        	}
        }
        if (arcsToDelete.size() == 0)
        {
        	System.out.println("There are no arcs on the selected nodes that can be deleted");
        	return;
        }
        new CircuitChangeJobs.DeleteArcs(arcsToDelete);
	}

	/****************************** DELETE A CELL ******************************/

	/**
	 * Method to delete a cell.
	 * @param cell the cell to delete.
	 * @param confirm true to prompt the user to confirm the deletion.
     * @param quiet true not to warn the user of the cell being used.
	 * @return true if the cell will be deleted (in a separate Job).
	 */
	public static boolean deleteCell(Cell cell, boolean confirm, boolean quiet)
	{
		// see if this cell is in use anywhere
		if (cell.isInUse("delete", quiet, true)) return false;

		// make sure the user really wants to delete the cell
		if (confirm)
		{
			int response = JOptionPane.showConfirmDialog(TopLevel.getCurrentJFrame(),
				"Are you sure you want to delete '" + cell + "'?", "Delete Cell Dialog", JOptionPane.YES_NO_OPTION);
			if (response != JOptionPane.YES_OPTION) return false;
		}

		// delete references to cell
		cleanCellRef(cell);

		// delete the cell
		new CellChangeJobs.DeleteCell(cell);
		return true;
	}

	/**
	 * Method to delete cell "cell".  Validity checks are assumed to be made (i.e. the
	 * cell is not used and is not locked).
	 */
	public static void cleanCellRef(Cell cell)
	{
		// delete random references to this cell
		Library lib = cell.getLibrary();
		if (cell == lib.getCurCell())
		    lib.setCurCell(null);

		// close windows that reference this cell
		for(Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext(); )
		{
			WindowFrame wf = it.next();
			WindowContent content = wf.getContent();
			if (content == null) continue;
			if (content.getCell() == cell)
			{
				if (!(content instanceof EditWindow))
				{
					wf.setCellWindow(null, null);
				} else
				{
					content.setCell(null, null, null);
					content.fullRepaint();
				}
			}
		}
	}

	/****************************** RENAME CELLS ******************************/

	public static void renameCellInJob(Cell cell, String newName)
	{
		// see if the rename should also regroup
		String newGroupCell = null;
        Set<Cell> set = new HashSet<Cell>();

//        for(Iterator<Cell> it = cell.getLibrary().getCells(); it.hasNext(); )
//		{
//			Cell oCell = it.next();
//
//            // Case when the new cell name defines a group already
//            if (oCell.getName().equalsIgnoreCase(newName) && oCell.getCellGroup() != null && oCell.getCellGroup() != cell.getCellGroup())
//			{
//				int response = JOptionPane.showConfirmDialog(TopLevel.getCurrentJFrame(),
//					"Also place the cell into the \"" + oCell.getCellGroup().getName() + "\" group?");
//				if (response == JOptionPane.YES_OPTION) newGroupCell = oCell.getName();
//				break;
//			}
//		}
        set.add(cell);
        if (cell.getNumVersions() > 1) // more than 1 version
        {
            int response = JOptionPane.showConfirmDialog(TopLevel.getCurrentJFrame(),
                "Also rename previous versions of the cell \"" + cell.getName() + "\" ?");
            if (response == JOptionPane.YES_OPTION)
            {
                for(Iterator<Cell> it = cell.getVersions(); it.hasNext(); )
                {
                    set.add(it.next());
                }
            }
        }
        for (Cell c : set)
            new CellChangeJobs.RenameCell(c, newName, newGroupCell);
	}

	public static void renameCellGroupInJob(Cell.CellGroup cellGroup, String newName)
	{
		new CellChangeJobs.RenameCellGroup(cellGroup.getCells().next(), newName);
	}

	/****************************** SHOW CELLS GRAPHICALLY ******************************/

	/**
	 * Method to graph the cells, starting from the current cell.
	 */
	public static void graphCellsFromCell()
	{
		Cell top = WindowFrame.needCurCell();
		if (top == null) return;
		new CellChangeJobs.GraphCells(top);
	}

	/**
	 * Method to graph all cells in the current Library.
	 */
	public static void graphCellsInLibrary()
	{
		new CellChangeJobs.GraphCells(null);
	}

	/**
	 * Method to graph all Library dependencies.
	 */
	public static void graphLibraries()
	{
		new CellChangeJobs.GraphLibraries();
	}

	/****************************** EXTRACT CELL INSTANCES ******************************/

	/**
	 * Method to package the selected objects into a new cell.
	 */
	public static void packageIntoCell()
	{
        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf == null) return;
        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter == null) return;

		// get the specified area
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;
		Cell curCell = wnd.getCell();
		if (curCell == null)
		{
			System.out.println("No cell in this window");
			return;
		}
		Rectangle2D bounds = highlighter.getHighlightedArea(wnd);
		if (bounds == null)
		{
			System.out.println("Must first select circuitry to package");
			return;
		}

		String newCellName = JOptionPane.showInputDialog("New cell name:", curCell.getName());
		if (newCellName == null) return;
		newCellName += curCell.getView().getAbbreviationExtension();

		Set<Geometric> whatToPackage = new HashSet<Geometric>();
		List<Geometric> highlighted = highlighter.getHighlightedEObjs(true, true);
		for(Geometric geom : highlighted)
		{
			whatToPackage.add(geom);
			if (geom instanceof ArcInst)
			{
				ArcInst ai = (ArcInst)geom;
				whatToPackage.add(ai.getHeadPortInst().getNodeInst());
				whatToPackage.add(ai.getTailPortInst().getNodeInst());
			}
		}
		new CellChangeJobs.PackageCell(curCell, whatToPackage, newCellName);
	}

	/**
	 * Method to yank the contents of complex node instance "topno" into its
	 * parent cell.
	 */
	public static void extractCells(int depth)
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;

		if (depth < 0)
		{
			Object obj = JOptionPane.showInputDialog("Number of levels to extract", "1");
			if (obj != null) depth = TextUtils.atoi((String)obj);
			if (depth <= 0) return;
		}

		List<NodeInst> selected = MenuCommands.getSelectedNodes();
		List<NodeInst> instances = new ArrayList<NodeInst>();
		int schematicCells = 0;
		for(NodeInst ni : selected)
		{
			if (!ni.isCellInstance()) continue;
			if (!ni.getProto().getTechnology().isLayout())
			{
				schematicCells++;
				continue;
			}
			instances.add(ni);
		}
		if (schematicCells > 0)
			System.out.println("WARNING: Cannot extract non-layout cells (" + schematicCells + " selected)");
		if (instances.size() == 0)
		{
			String msg = "No extraction done";
			if (schematicCells == 0) msg = "No cell instances are selected..." + msg;
			System.out.println(msg);
			return;
		}
		new CellChangeJobs.ExtractCellInstances(cell, instances, depth, User.isExtractCopiesExports(),
			User.isIncrementRightmostIndex(), false);
	}

	/****************************** CLEAN-UP ******************************/

	public static void cleanupPinsCommand(boolean everywhere, EditingPreferences ep)
	{
        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf == null) return;
        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter == null) return;

		if (everywhere)
		{
			boolean cleaned = false;
			for(Library lib : Library.getVisibleLibraries())
			{
				for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
				{
					Cell cell = it.next();
					if (cleanupCell(cell, false, highlighter, ep)) cleaned = true;
				}
			}
			if (!cleaned) System.out.println("Nothing to clean");
		} else
		{
			// just cleanup the current cell
            Cell cell = WindowFrame.needCurCell();
			if (cell == null) return;
			cleanupCell(cell, true, highlighter, ep);
		}
	}

	/**
	 * Method to clean-up cell "np" as follows:
	 *   remove stranded pins
	 *   collapse redundant (inline) arcs
	 *   highlight zero-size nodes
	 *   removes duplicate arcs
	 *   highlight oversize pins that allow arcs to connect without touching
	 *   move unattached and invisible pins with text in a different location
	 *   resize oversized pins that don't have oversized arcs on them
	 * Returns true if changes are made.
	 */
	private static boolean cleanupCell(Cell cell, boolean justThis, Highlighter highlighter, EditingPreferences ep)
	{
		// look for unused pins that can be deleted
		Set<NodeInst> pinsToRemove = new HashSet<NodeInst>();
		List<CircuitChangeJobs.Reconnect> pinsToPassThrough = CircuitChangeJobs.getPinsToPassThrough(cell, ep);
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (!ni.getFunction().isPin()) continue;

			// if the pin is an export, save it
			if (ni.hasExports()) continue;

			// if the pin is not connected or displayed, delete it
			if (!ni.hasConnections())
			{
				// see if the pin has displayable variables on it
				boolean hasDisplayable = false;
				for(Iterator<Variable> vIt = ni.getVariables(); vIt.hasNext(); )
				{
					Variable var = vIt.next();
					if (var.isDisplay()) { hasDisplayable = true;   break; }
				}
				if (hasDisplayable) continue;

				// no displayable variables: delete it
				pinsToRemove.add(ni);
				continue;
			}
		}

		// look for oversized pins that can be reduced in size
		Map<NodeInst,EPoint> pinsToScale = new HashMap<NodeInst,EPoint>();
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (!ni.getFunction().isPin()) continue;

			// if the pin is standard size, leave it alone
			double overSizeX = ni.getXSize() - ni.getProto().getDefWidth(ep);
			if (overSizeX < 0) overSizeX = 0;
			double overSizeY = ni.getYSize() - ni.getProto().getDefHeight(ep);
			if (overSizeY < 0) overSizeY = 0;
			if (overSizeX == 0 && overSizeY == 0) continue;

			// all arcs must connect in the pin center
			boolean arcsInCenter = true;
			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
			{
				Connection con = cIt.next();
				ArcInst ai = con.getArc();
				if (ai.getHeadPortInst().getNodeInst() == ni)
				{
					if (ai.getHeadLocation().getX() != ni.getAnchorCenterX()) { arcsInCenter = false;   break; }
					if (ai.getHeadLocation().getY() != ni.getAnchorCenterY()) { arcsInCenter = false;   break; }
				}
				if (ai.getTailPortInst().getNodeInst() == ni)
				{
					if (ai.getTailLocation().getX() != ni.getAnchorCenterX()) { arcsInCenter = false;   break; }
					if (ai.getTailLocation().getY() != ni.getAnchorCenterY()) { arcsInCenter = false;   break; }
				}
			}
			if (!arcsInCenter) continue;

			// look for arcs that are oversized
			double overSizeArc = 0;
			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
			{
				Connection con = cIt.next();
				ArcInst ai = con.getArc();
				double overSize = ai.getLambdaBaseWidth() - ai.getProto().getDefaultLambdaBaseWidth(ep);
				if (overSize < 0) overSize = 0;
				if (overSize > overSizeArc) overSizeArc = overSize;
			}

			// if an arc covers the pin, leave the pin
			if (overSizeArc >= overSizeX && overSizeArc >= overSizeY) continue;

			double dSX = 0, dSY = 0;
			if (overSizeArc < overSizeX) dSX = overSizeX - overSizeArc;
			if (overSizeArc < overSizeY) dSY = overSizeY - overSizeArc;
			pinsToScale.put(ni, EPoint.fromLambda(-dSX, -dSY));
		}

		// look for pins that are invisible and have text in different location
		List<NodeInst> textToMove = new ArrayList<NodeInst>();
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			Point2D pt = ni.invisiblePinWithOffsetText(false);
			if (pt != null)
				textToMove.add(ni);
		}

		// highlight oversize pins that allow arcs to connect without touching
		int overSizePins = 0;
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (!ni.getFunction().isPin()) continue;

			// make sure all arcs touch each other
			boolean nodeIsBad = false;
			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
			{
				Connection con = cIt.next();
				ArcInst ai = con.getArc();
				Poly poly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.FILLED);
				for(Iterator<Connection> oCIt = ni.getConnections(); oCIt.hasNext(); )
				{
					Connection oCon = oCIt.next();
					ArcInst oAi = oCon.getArc();
					if (ai.getArcId() <= oAi.getArcId()) continue;
					Poly oPoly = oAi.makeLambdaPoly(oAi.getGridBaseWidth(), Poly.Type.FILLED);
					double dist = poly.separation(oPoly);
					if (dist <= 0) continue;
					nodeIsBad = true;
					break;
				}
				if (nodeIsBad) break;
			}
			if (nodeIsBad)
			{
				if (justThis)
				{
					highlighter.addElectricObject(ni, cell);
				}
				overSizePins++;
			}
		}

		// look for duplicate arcs
		Set<ArcInst> arcsToKill = new HashSet<ArcInst>();
        for (Iterator<ArcInst> ait = cell.getArcs(); ait.hasNext(); ) {
            ArcInst ai = ait.next();
            int arcId = ai.getArcId();
            if (arcsToKill.contains(ai)) continue;
            PortInst pi = ai.getHeadPortInst();
            for (Iterator<Connection> it = pi.getConnections(); it.hasNext(); ) {
                Connection con = it.next();
                ArcInst oAi = con.getArc();
                if (oAi.getArcId() >= arcId) continue;
                if (ai.getProto() != oAi.getProto()) continue;
                int otherEnd = 1 - con.getEndIndex();
                PortInst oPi = oAi.getPortInst(otherEnd);
                if (oPi != ai.getTailPortInst()) continue;
                arcsToKill.add(oAi);
            }
        }

//		// look for duplicate arcs
//		HashMap arcsToKill = new HashMap();
//		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
//		{
//			NodeInst ni = it.next();
//			for(Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext(); )
//			{
//				Connection con = cIt.next();
//				ArcInst ai = con.getArc();
//                int otherEnd = 1 - con.getEndIndex();
////				int otherEnd = 0;
////				if (ai.getConnection(0) == con) otherEnd = 1;
//				boolean foundAnother = false;
//				for(Iterator<Connection> oCIt = ni.getConnections(); oCIt.hasNext(); )
//				{
//					Connection oCon = oCIt.next();
//					ArcInst oAi = oCon.getArc();
//					if (ai.getArcIndex() <= oAi.getArcIndex()) continue;
//					if (con.getPortInst().getPortProto() != oCon.getPortInst().getPortProto()) continue;
//					if (ai.getProto() != oAi.getProto()) continue;
//                    int oOtherEnd = 1 - oCon.getEndIndex();
////					int oOtherEnd = 0;
////					if (oAi.getConnection(0) == oCon) oOtherEnd = 1;
//					if (ai.getPortInst(otherEnd).getNodeInst() !=
//						oAi.getPortInst(oOtherEnd).getNodeInst()) continue;
//					if (ai.getPortInst(otherEnd).getPortProto() !=
//						oAi.getPortInst(oOtherEnd).getPortProto()) continue;
//
//					// this arc is a duplicate
//					arcsToKill.put(oAi, oAi);
//					foundAnother = true;
//					break;
//				}
//				if (foundAnother) break;
//			}
//		}

		// now highlight negative or zero-size nodes
		int zeroSize = 0, negSize = 0;
		for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
		{
			NodeInst ni = it.next();
			if (Generic.isCellCenterOrEssentialBnd(ni) ||
				ni.getProto() == Generic.tech().invisiblePinNode ||
				ni.getProto() == Generic.tech().universalPinNode) continue;
			double sX = ni.getLambdaBaseXSize();
			double sY = ni.getLambdaBaseYSize();
			if (sX > 0 && sY > 0) continue;
			if (sX > 0 || sY > 0 && ni.getProto().getTechnology() == Artwork.tech()) continue;
			if (sX == 0 && sY == 0 && ni.getFunction().isPin()) continue;
			if (justThis)
			{
				highlighter.addElectricObject(ni, cell);
			}
			if (sX < 0 || sY < 0) negSize++; else
				zeroSize++;
		}

		if (pinsToRemove.isEmpty() &&
			pinsToPassThrough.isEmpty() &&
			pinsToScale.isEmpty() &&
			zeroSize == 0 &&
			negSize == 0 &&
			textToMove.isEmpty() &&
			overSizePins == 0 &&
			arcsToKill.size() == 0)
		{
			if (justThis) System.out.println("Nothing to clean");
			return false;
		}

		CircuitChangeJobs.CleanupChanges ccJob = new CircuitChangeJobs.CleanupChanges(cell, justThis, pinsToRemove, pinsToPassThrough, pinsToScale, textToMove, arcsToKill,
			zeroSize, negSize, overSizePins);
		ccJob.startJob();
		return true;
	}

	/**
	 * Method to analyze the current cell and show all nonmanhattan geometry.
	 */
	public static void showNonmanhattanCommand()
	{
		Cell curCell = WindowFrame.needCurCell();
		if (curCell == null) return;

        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf == null) return;
        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter == null) return;

		// see which cells (in any library) have nonmanhattan stuff
        Set<Cell> cellsSeen = new HashSet<Cell>();
		for(Iterator<Library> lIt = Library.getLibraries(); lIt.hasNext(); )
		{
			Library lib = lIt.next();
			for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
			{
				Cell cell = cIt.next();
				if (cell.getView() == View.ICON || cell.getView() == View.SCHEMATIC) continue;
				for(Iterator<ArcInst> aIt = cell.getArcs(); aIt.hasNext(); )
				{
					ArcInst ai = aIt.next();
					ArcProto ap = ai.getProto();
					if (ap.getTechnology() == Generic.tech() || ap.getTechnology() == Artwork.tech() ||
						ap.getTechnology() == Schematics.tech()) continue;
					Variable var = ai.getVar(ImmutableArcInst.ARC_RADIUS);
					if (var != null) cellsSeen.add(cell);
					if (ai.getHeadLocation().getX() != ai.getTailLocation().getX() &&
						ai.getHeadLocation().getY() != ai.getTailLocation().getY())
							cellsSeen.add(cell);
				}
				for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
				{
					NodeInst ni = nIt.next();
					if ((ni.getAngle() % 900) != 0) cellsSeen.add(cell);
				}
			}
		}

		// show the nonmanhattan things in the current cell
		int i = 0;
		for(Iterator<ArcInst> aIt = curCell.getArcs(); aIt.hasNext(); )
		{
			ArcInst ai = aIt.next();
			ArcProto ap = ai.getProto();
			if (ap.getTechnology() == Generic.tech() || ap.getTechnology() == Artwork.tech() ||
				ap.getTechnology() == Schematics.tech()) continue;
			boolean nonMan = false;
			Variable var = ai.getVar(ImmutableArcInst.ARC_RADIUS);
			if (var != null) nonMan = true;
			if (ai.getHeadLocation().getX() != ai.getTailLocation().getX() &&
				ai.getHeadLocation().getY() != ai.getTailLocation().getY())
					nonMan = true;
			if (nonMan)
			{
				if (i == 0) highlighter.clear();
				highlighter.addElectricObject(ai, curCell);
				i++;
			}
		}
		for(Iterator<NodeInst> nIt = curCell.getNodes(); nIt.hasNext(); )
		{
			NodeInst ni = nIt.next();
			if ((ni.getAngle() % 900) == 0) continue;
			if (i == 0) highlighter.clear();
			highlighter.addElectricObject(ni, curCell);
			i++;
		}
		if (i == 0) System.out.println("No nonmanhattan objects in this cell"); else
		{
			highlighter.finished();
			System.out.println(i + " objects are not manhattan in this cell");
		}

		// tell about other non-manhatten-ness elsewhere
		for(Iterator<Library> lIt = Library.getLibraries(); lIt.hasNext(); )
		{
			Library lib = lIt.next();
			if (lib.isHidden()) continue;
			int numBad = 0;
			for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
			{
				Cell cell = cIt.next();
				if (cellsSeen.contains(cell) && cell != curCell) numBad++;
			}
			if (numBad == 0) continue;

			int cellsFound = 0;
			String infstr = "";
			for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
			{
				Cell cell = cIt.next();
				if (cell == curCell || !cellsSeen.contains(cell)) continue;
				if (cellsFound > 0) infstr += " ";
				infstr += cell.describe(true);
				cellsFound++;
			}
			if (cellsFound == 1)
			{
				System.out.println("Library " + lib.getName() + " has nonmanhattan geometry in cell " + infstr);
			} else
			{
				System.out.println("Library " + lib.getName() + " has nonmanhattan geometry in these cells: " + infstr);
			}
		}
	}

	/**
	 * Method to highlight all pure layer nodes in the current cell.
	 */
	public static void showPureLayerCommand()
	{
		Cell curCell = WindowFrame.needCurCell();
		if (curCell == null) return;

        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf == null) return;
        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter == null) return;

		// show the pure layer nodes in the current cell
		int i = 0;
		for(Iterator<NodeInst> nIt = curCell.getNodes(); nIt.hasNext(); )
		{
			NodeInst ni = nIt.next();
			if (ni.getFunction() != PrimitiveNode.Function.NODE) continue;
			if (i == 0) highlighter.clear();
			highlighter.addElectricObject(ni, curCell);
			i++;
		}
		if (i == 0) System.out.println("No pure layer nodes in this cell"); else
		{
			highlighter.finished();
			System.out.println(i + " pure layer nodes in this cell");
		}
	}

	/**
	 * Method to shorten all selected arcs.
	 * Since arcs may connect anywhere inside of the ports on nodes, a port with nonzero area will allow an arc
	 * to move freely.
	 * This command shortens selected arcs so that their endpoints arrive at the part of the node that allows the shortest arc.
	 */
	public static void shortenArcsCommand()
	{
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		new CircuitChangeJobs.ShortenArcs(cell, MenuCommands.getSelectedArcs());
	}

	/**
	 * Method to show all redundant pure-layer nodes in the Cell.
	 * It works only for rectangular pure-layer nodes that are inside of other
	 * (possibly nonrectangular) nodes.
	 * It works on visible layers only.
	 */
	public static void showRedundantPureLayerNodes()
	{
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;
        LayerVisibility lv = wnd.getLayerVisibility();
		Cell cell = WindowFrame.needCurCell();
		if (cell == null) return;
		Set<NodeInst> redundantPures = new HashSet<NodeInst>();
		for(Iterator<Layer> it = cell.getTechnology().getLayers(); it.hasNext(); )
		{
			Layer lay = it.next();
			if (!lv.isVisible(lay)) continue;
			PrimitiveNode pNp = lay.getPureLayerNode();
			if (pNp == null) continue;

			// find all pure-layer nodes in the cell
			List<NodeInst> allPures = new ArrayList<NodeInst>();
			Map<NodeInst,Double> pureAreas = new HashMap<NodeInst,Double>();
			RTNode<NodeInst> root = RTNode.makeTopLevel();
			for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
			{
				NodeInst ni = nIt.next();
				if (ni.getProto() != pNp) continue;
				allPures.add(ni);
				Point2D [] points = ni.getTrace();
				double area;
				if (points == null) area = ni.getXSize() * ni.getYSize(); else
				{
					boolean hasGaps = false;
					for(int i=0; i<points.length; i++) if (points[i] == null) { hasGaps = true;   break; }
					if (hasGaps)
					{
						// outline has multiple disjoint polygons
						area = 0;
						int start = 0;
						for(int i=0; i<points.length; i++)
						{
							if (i == points.length-1 || points[i+1] == null)
							{
								Point2D [] segment = new Point2D[i-start+1];
								for(int j=start; j<=i; j++)
									segment[j-start] = points[j];
								area += GenMath.getAreaOfPoints(segment);
								start = i+2;
							}
						}
					} else
					{
						area = GenMath.getAreaOfPoints(points);
					}
				}
				pureAreas.put(ni, new Double(area));
				root = RTNode.linkGeom(null, root, ni);
			}

			// now find the redundant ones
			for(NodeInst ni : allPures)
			{
				Double nodeArea = pureAreas.get(ni);
				Rectangle2D nodeRect = ni.getBounds();
				for(Iterator<NodeInst> sea = new RTNode.Search<NodeInst>(nodeRect, root, false); sea.hasNext(); )
				{
					NodeInst neighbor = (NodeInst)sea.next();
					if (neighbor == ni) continue;
					Double neighborArea = pureAreas.get(neighbor);
					if (neighborArea.doubleValue() < nodeArea.doubleValue()) continue;
					if (redundantPures.contains(neighbor)) continue;
					PolyBase [] neighborPolys = neighbor.getProto().getTechnology().getShapeOfNode(neighbor);
					PolyBase neighborPoly = neighborPolys[0];
					if (neighborPoly.contains(nodeRect))
					{
						redundantPures.add(ni);
						break;
					}
				}
			}
		}

		// show them
		wnd.clearHighlighting();
		for(NodeInst ni : redundantPures)
			wnd.addElectricObject(ni, cell);
		wnd.finishedHighlighting();
		System.out.println("Highlighted " + redundantPures.size() + " redundant pure-layer nodes");
	}

	/**
	 * Method to return a Rectangle that describes the orthogonal box in this Poly.
	 * @return the Rectangle that describes this Poly.
	 * If the Poly is not an orthogonal box, returns null.
	 * IT IS NOT PERMITTED TO MODIFY THE RETURNED RECTANGLE
	 * (because it is taken from the internal bounds of the Poly).
	 */
	public static boolean isBox(Point2D [] points)
	{
		if (points.length == 4)
		{
		} else if (points.length == 5)
		{
			if (points[0].getX() != points[4].getX() || points[0].getY() != points[4].getY()) return false;
		} else return false;

		// make sure the polygon is rectangular and orthogonal
		if (points[0].getX() == points[1].getX() && points[2].getX() == points[3].getX() &&
			points[0].getY() == points[3].getY() && points[1].getY() == points[2].getY())
		{
			return true;
		}
		if (points[0].getX() == points[3].getX() && points[1].getX() == points[2].getX() &&
			points[0].getY() == points[1].getY() && points[2].getY() == points[3].getY())
		{
			return true;
		}
		return false;
	}
	/****************************** MAKE A NEW VERSION OF A CELL ******************************/

	public static void newVersionOfCell(Cell cell)
	{
		// disallow if in Project Management
		int status = Project.getCellStatus(cell);
		if (status != Project.NOTMANAGED)
		{
			JOptionPane.showMessageDialog(TopLevel.getCurrentJFrame(),
				"This cell is part of a project.  To get a new version of it, check it out.", "Cannot Make New Version",
				JOptionPane.ERROR_MESSAGE);
			return;
		}
		new CellChangeJobs.NewCellVersion(cell);
	}

	/****************************** WIRE SELECTED OBJECTS ******************************/

	/**
	 * Method to connect the selected arcs that overlap a network selected from a list.
	 */
	public static void connectOverlappingNetworks()
	{
		// get current cell and selection
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf == null) return;
		Cell cell = wf.getContent().getCell();
		Highlighter highlighter = wf.getContent().getHighlighter();
		if (highlighter == null) return;
		List<Highlight> highlighted = highlighter.getHighlights();

		// find out the name of the destination network
		GetNetName gnn = new GetNetName(cell);
		String netName = gnn.getSelectedNetwork();
		if (netName == null) return;

		List<ArcInst> arcsToConnect = new ArrayList<ArcInst>();
		for(Highlight h : highlighted)
		{
			if (!h.isHighlightEOBJ()) continue;
			ElectricObject eObj = h.getElectricObject();
			if (eObj instanceof ArcInst)
			{
				ArcInst ai = (ArcInst)eObj;
				arcsToConnect.add(ai);
			}
		}

		// route them all
		new RouteOverlapJob(cell, arcsToConnect, netName);

//		// get set of Networks already under consideration, including the destination network
//		Set<Network> netsAlready = new HashSet<Network>();
//        Netlist nl = cell.getNetlist();  
//		for(Iterator<Network> it = nl.getNetworks(); it.hasNext(); )
//		{
//			Network n = it.next();
//			String nn = n.describe(false);
//			if (nn.equals(netName)) { netsAlready.add(n);  break; }
//		}
//
//		// get network-unique list of selected ports to connect
//		List<PortInst> portsToWire = new ArrayList<PortInst>();
//		for(Highlight h : highlighted)
//		{
//			if (!h.isHighlightEOBJ()) continue;
//			ElectricObject eObj = h.getElectricObject();
//			if (eObj instanceof ArcInst)
//			{
//				ArcInst ai = (ArcInst)eObj;
//				Network net = nl.getNetwork(ai, 0);
//				if (netsAlready.contains(net)) continue;
//				netsAlready.add(net);
//				NodeInst ni1 = ai.getHeadPortInst().getNodeInst();
//				NodeInst ni2 = ai.getTailPortInst().getNodeInst();
//				if (ni1.isCellInstance()) ni1 = null;
//				if (ni2.isCellInstance()) ni2 = null;
//				if (ni1 != null && ni2 != null && ni1 == ni2) ni2 = null;
//				if (ni1 != null && ni2 != null)
//				{
//					if (ni1.getNumConnections() > ni2.getNumConnections()) ni1 = null; else
//						if (ni2.getNumConnections() > ni1.getNumConnections()) ni2 = null;
//				}
//				if (ni1 != null) portsToWire.add(ai.getHeadPortInst()); else
//					portsToWire.add(ai.getTailPortInst());
//			}
//		}
//		if (portsToWire.size() == 0)
//		{
//			System.out.println("Must first select arcs not on network " + netName + " that will connect to it");
//			return;
//		}
//
//		// route them all
//		new DoAllRoutesJob(cell, portsToWire, netName);
	}

	/**
	 * Class to connect a set of arcs where they cross a named network.
	 */
	private static class RouteOverlapJob extends Job
	{
		/** cell in which to build route */ private Cell cell;
		/** Arcs to route */                private List<ArcInst> arcsToConnect;
		/** Net to connect */               private String netName;

		public RouteOverlapJob(Cell cell, List<ArcInst> arcsToConnect, String netName)
		{
			super("Connect arcs to network", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.arcsToConnect = arcsToConnect;
			this.netName = netName;
			startJob();
		}

		@Override
		public boolean doIt() throws JobException
		{
			if (CircuitChangeJobs.cantEdit(cell, null, true, false, true) != 0) return false;
			EditingPreferences ep = getEditingPreferences();
			for(ArcInst aiConnect : arcsToConnect)
			{
				// get the network to connect this arc to
				Netlist nl = cell.getNetlist();  
				Network net = null;
				for(Iterator<Network> it = nl.getNetworks(); it.hasNext(); )
				{
					Network n = it.next();
					String nn = n.describe(false);
					if (nn.contains(netName)) { net = n;  break; }
				}

				// get information about this arc
				EPoint conHeadLoc = aiConnect.getHeadLocation();
				EPoint conTailLoc = aiConnect.getTailLocation();
				ERectangle conBound = aiConnect.getBounds();

				// search for anything under the arc on that network
				List<NodeArcPair> newNodes = new ArrayList<NodeArcPair>();
				for(Iterator<Geometric> it = cell.searchIterator(conBound); it.hasNext(); )
				{
					Geometric geom = it.next();
					if (geom instanceof ArcInst)
					{
						ArcInst ai = (ArcInst)geom;
						if (nl.getNetwork(ai, 0) != net) continue;

						// found arc on destination network that intersects selected arc. See where the connection lies
						EPoint aiHeadLoc = ai.getHeadLocation();
						EPoint aiTailLoc = ai.getTailLocation();
						Point2D intersect = GenMath.intersect(conHeadLoc, aiConnect.getAngle(), ai.getHeadLocation(), ai.getAngle());
						if (intersect == null) continue;
						if (intersect.getX() < Math.min(conHeadLoc.getX(), conTailLoc.getX())) continue;
						if (intersect.getX() > Math.max(conHeadLoc.getX(), conTailLoc.getX())) continue;
						if (intersect.getX() < Math.min(aiHeadLoc.getX(), aiTailLoc.getX())) continue;
						if (intersect.getX() > Math.max(aiHeadLoc.getX(), aiTailLoc.getX())) continue;
						EPoint intersectEP = EPoint.fromLambda(intersect.getX(), intersect.getY());

						// connection point found: find possible vias that connect them
						Technology tech = aiConnect.getProto().getTechnology();
						List<PrimitiveNode> possibleConnections = new ArrayList<PrimitiveNode>();
						for(Iterator<PrimitiveNode> nIt = tech.getNodes(); nIt.hasNext(); )
						{
							PrimitiveNode pnp = nIt.next();
							if (pnp.getFunction() != PrimitiveNode.Function.CONTACT) continue;
							if (pnp.connectsTo(aiConnect.getProto()) != null && pnp.connectsTo(ai.getProto()) != null)
								possibleConnections.add(pnp);
						}
						if (possibleConnections.size() == 0) continue;

						// sort the possibilities so that the largest ones are tried first
						Collections.sort(possibleConnections, new PrimsBySize(ep));

						for(PrimitiveNode pnp : possibleConnections)
						{
							NodeInst ni = NodeInst.makeDummyInstance(pnp, ep, intersectEP, pnp.getDefWidth(ep),
								pnp.getDefHeight(ep), Orientation.IDENT);
							if (fitsInArcs(ni, aiConnect, ai))
							{
								newNodes.add(new NodeArcPair(ni, ai));
								break;
							}
							ni = NodeInst.makeDummyInstance(pnp, ep, intersectEP, pnp.getDefWidth(ep),
								pnp.getDefHeight(ep), Orientation.R);
							if (fitsInArcs(ni, aiConnect, ai))
							{
								newNodes.add(new NodeArcPair(ni, ai));
								break;
							}
						}
					}
				}

				// place the vias along the arc
				if (newNodes.size() == 0) continue;
				List<PortInst> portsAlongTheWay = new ArrayList<PortInst>();
				for(NodeArcPair nap : newNodes)
				{
					NodeInst dummyNi = nap.getNodeInst();
					NodeInst ni = NodeInst.makeInstance(dummyNi.getProto(), ep, dummyNi.getTrueCenter(), dummyNi.getXSize(),
						dummyNi.getYSize(), cell, dummyNi.getOrient(), null);
					portsAlongTheWay.add(ni.getOnlyPortInst());

					ArcInst ai = nap.getArcInst();
					String arcName = ai.getName();
					ArcInst ai1 = ArcInst.makeInstanceBase(ai.getProto(), ep, ai.getLambdaBaseWidth(), ai.getHeadPortInst(), ni.getOnlyPortInst());
					ai1.setHeadExtended(ai.isHeadExtended());
					ai1.setAngle(ai.getAngle());
					ArcInst ai2 = ArcInst.makeInstanceBase(ai.getProto(), ep, ai.getLambdaBaseWidth(), ni.getOnlyPortInst(), ai.getTailPortInst());
					ai2.setHeadExtended(ai.isTailExtended());
					ai2.setAngle(ai.getAngle());
					ai.kill();
	                if (arcName != null)
	                {
	                    if (ai1.getLambdaLength() > ai2.getLambdaLength())
	                    {
	                    	ai1.setName(arcName, ep);
	                    	ai1.copyTextDescriptorFrom(ai, ArcInst.ARC_NAME);
	                    } else
	                    {
	                    	ai2.setName(arcName, ep);
	                    	ai2.copyTextDescriptorFrom(ai, ArcInst.ARC_NAME);
	                    }
	                }
				}
				portsAlongTheWay.add(aiConnect.getHeadPortInst());
				portsAlongTheWay.add(aiConnect.getTailPortInst());
				Collections.sort(portsAlongTheWay, new PortsByLocation());
				ArcInst longestAi = null;
				double longestAiLength = 0;
				boolean firstExtend, lastExtend;
				if (portsAlongTheWay.get(0) == aiConnect.getHeadPortInst())
				{
					firstExtend = aiConnect.isHeadExtended();
					lastExtend = aiConnect.isTailExtended();
				} else
				{
					firstExtend = aiConnect.isTailExtended();
					lastExtend = aiConnect.isHeadExtended();
				}
				for(int i=1; i<portsAlongTheWay.size(); i++)
				{
					PortInst prevPort = portsAlongTheWay.get(i-1);
					PortInst thisPort = portsAlongTheWay.get(i);
					ArcInst ai = ArcInst.makeInstanceBase(aiConnect.getProto(), ep, aiConnect.getLambdaBaseWidth(), prevPort, thisPort);
					if (longestAi == null || ai.getLambdaLength() > longestAiLength)
					{
						longestAi = ai;
						longestAiLength = ai.getLambdaLength();
					}
					ai.setAngle(aiConnect.getAngle());
					if (i == 1) ai.setHeadExtended(firstExtend);
					if (i == portsAlongTheWay.size()-1) ai.setTailExtended(lastExtend);
				}
				String arcName = aiConnect.getName();
				aiConnect.kill();
				if (arcName != null)
				{
					longestAi.setName(arcName, ep);
					longestAi.copyTextDescriptorFrom(aiConnect, ArcInst.ARC_NAME);
				}
			}
			return true;
		}

		private boolean fitsInArcs(NodeInst ni, ArcInst ai1, ArcInst ai2)
		{
			// get the metal layers in the two arcs
			Poly[] polys1 = ai1.getProto().getTechnology().getShapeOfArc(ai1, null);
			Poly[] polys2 = ai2.getProto().getTechnology().getShapeOfArc(ai2, null);
			List<Poly> possiblePolys = new ArrayList<Poly>();
			for(Poly p : polys1)
			{
				Layer lay = p.getLayer();
				if (lay != null && lay.getFunction().isMetal()) possiblePolys.add(p);
			}
			for(Poly p : polys2)
			{
				Layer lay = p.getLayer();
				if (lay != null && lay.getFunction().isMetal()) possiblePolys.add(p);
			}

			// now see if the metal layers all fit in these polygons
			Poly[] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
			FixpTransform trans = ni.rotateOut();
			for(Poly p : polys)
			{
				Layer lay = p.getLayer();
				if (lay == null || !lay.getFunction().isMetal()) continue;
				p.transform(trans);
				double lX = p.getBounds2D().getMinX();
				double hX = p.getBounds2D().getMaxX();
				double lY = p.getBounds2D().getMinY();
				double hY = p.getBounds2D().getMaxY();
				boolean nodeLayerFits = false;
				for(Poly pArc : possiblePolys)
				{
					if (pArc.getLayer() != lay) continue;
					if (pArc.getBounds2D().getMinX() > lX) continue;
					if (pArc.getBounds2D().getMaxX() < hX) continue;
					if (pArc.getBounds2D().getMinY() > lY) continue;
					if (pArc.getBounds2D().getMaxY() < hY) continue;
					nodeLayerFits = true;
					break;
				}
				if (!nodeLayerFits) return false;
			}
			return true;
		}
	}

	private static class NodeArcPair
	{
		private NodeInst ni;
		private ArcInst ai;

		public NodeArcPair(NodeInst ni, ArcInst ai)
		{
			this.ni = ni;
			this.ai = ai;
		}

		public NodeInst getNodeInst() { return ni; }

		public ArcInst getArcInst() { return ai; }
	}

	/**
	 * Comparator class for sorting nodes so they are in a line
	 */
	private static class PortsByLocation implements Comparator<PortInst>
	{
		public int compare(PortInst pi1, PortInst pi2)
		{
			if (pi1.getCenter().getX() < pi2.getCenter().getX()) return 1;
			if (pi1.getCenter().getX() > pi2.getCenter().getX()) return -1;
			if (pi1.getCenter().getY() < pi2.getCenter().getY()) return 1;
			if (pi1.getCenter().getY() > pi2.getCenter().getY()) return -1;
			return 0;
		}
	}

	/**
	 * Comparator class for sorting primitives by their size.
	 */
	private static class PrimsBySize implements Comparator<PrimitiveNode>
	{
		private EditingPreferences ep;

		public PrimsBySize(EditingPreferences ep) { this.ep = ep; }

		public int compare(PrimitiveNode pn1, PrimitiveNode pn2)
		{
			double sz1 = pn1.getDefWidth(ep) * pn1.getDefHeight(ep);
			double sz2 = pn2.getDefWidth(ep) * pn2.getDefHeight(ep);
			if (sz1 < sz2) return 1;
			if (sz1 > sz2) return -1;
			return 0;
		}
	}

//	/**
//	 * Class to route a set of ports to a named network.
//	 */
//	private static class DoAllRoutesJob extends Job
//    {
//        /** cell in which to build route */ private Cell cell;
//        /** Routes to build */              private List<PortInst> portsToWire;
//        /** Net to connect */               private String netName;
//
//        public DoAllRoutesJob(Cell cell, List<PortInst> portsToWire, String netName)
//        {
//            super("Connect arcs to network", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
//            this.cell = cell;
//            this.portsToWire = portsToWire;
//            this.netName = netName;
//            startJob();
//        }
//
//        @Override
//        public boolean doIt() throws JobException
//        {
//            if (CircuitChangeJobs.cantEdit(cell, null, true, false, true) != 0) return false;
//            EditingPreferences ep = getEditingPreferences();
//
//    		// do the wiring
//    		InteractiveRouter router = new SimpleWirer(ep);
//    		Map<ArcProto,Integer> arcsCreatedMap = new HashMap<ArcProto,Integer>();
//    		Map<NodeProto,Integer> nodesCreatedMap = new HashMap<NodeProto,Integer>();
//    		for(PortInst pi : portsToWire)
//    		{
//                Netlist nl = cell.getNetlist();  
//        		Network net = null;
//        		for(Iterator<Network> it = nl.getNetworks(); it.hasNext(); )
//        		{
//        			Network n = it.next();
//        			String nn = n.describe(false);
//        			if (nn.contains(netName)) { net = n;  break; }
//        		}
//
//        		if (net == null)
//        		{
//    				System.out.println("Cannot find network '" + netName + "' in netlist for port '" + pi.describe(false) + "'");
//    				continue;
//        		}
//        		// find all arcs on the destination network
//    			Set<ArcInst> arcsOnNet = new HashSet<ArcInst>();
//    			for(Iterator<PortInst> it = net.getPorts(); it.hasNext(); )
//    			{
//    				PortInst p = it.next();
//    				for(Iterator<Connection> cIt = p.getConnections(); cIt.hasNext(); )
//    				{
//    					Connection con = cIt.next();
//    					arcsOnNet.add(con.getArc());
//    				}
//    			}
//
//    			// find arc on the destination network that is closest to the selected port
//    			EPoint ctr = pi.getCenter();
//    			ArcInst closest = null;
//    			double dist = 0;
//    			Point2D closestInter = null;
//    			for(ArcInst a : arcsOnNet)
//    			{
//    				Point2D inter = GenMath.intersect(a.getHeadLocation(), a.getAngle(), ctr, (a.getAngle()+900)%3600);
//    				double d = inter.distance(ctr);
//    				if (closest == null || d < dist)
//    				{
//    					closest = a;
//    					dist = d;
//    					closestInter = inter;
//    				}
//    			}
//    			if (closest == null)
//    			{
//    				System.out.println("Cannot figure out how to connect " + pi.getNodeInst().describe(false) + " to the network");
//    				continue;
//    			}
//
//    			Route route = router.planRoute(cell, pi, closest, closestInter, null, ep, true, true, null, null);
//            	Router.createRouteNoJob(route, cell, arcsCreatedMap, nodesCreatedMap, ep);
//    		}
//    		Router.reportRoutingResults("Bus connect", arcsCreatedMap, nodesCreatedMap, false);
//            return true;
//       }
//    }

	/**
	 * Class to present a list of networks in a Cell and return a selected network name.
	 */
	private static class GetNetName extends EDialog
	{
		private JList list;
		private DefaultListModel model;

		GetNetName(Cell cell)
		{
			super(TopLevel.getCurrentJFrame(), true);
			getContentPane().setLayout(new GridBagLayout());
			setTitle("Select Network");
			addWindowListener(new WindowAdapter()
			{
				public void windowClosing(WindowEvent evt) { closeDialog(); }
			});

			// make scrollable list of network names
			JScrollPane objectPane = new JScrollPane();
			objectPane.setMinimumSize(new Dimension(200, 200));
			objectPane.setPreferredSize(new Dimension(200, 200));
			GridBagConstraints gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 0;
			gridBagConstraints.gridwidth = 2;
			gridBagConstraints.fill = GridBagConstraints.BOTH;
			gridBagConstraints.weightx = 1.0;
			gridBagConstraints.weighty = 1.0;
			gridBagConstraints.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(objectPane, gridBagConstraints);
			model = new DefaultListModel();
			list = new JList(model);
			list.addMouseListener(new MouseAdapter() {
	            public void mouseClicked(MouseEvent evt) { if (evt != null && evt.getClickCount() >= 2) closeDialog(); }
	        });
			objectPane.setViewportView(list);

			// load the list of network names
			Netlist netlist = cell.getNetlist();
			if (netlist == null)
			{
				System.out.println("Sorry, a deadlock aborted selection (network information unavailable).  Please try again");
				return;
			}
			List<String> netNames = new ArrayList<String>();
			for(Iterator<Network> it = netlist.getNetworks(); it.hasNext(); )
			{
				Network net = it.next();
				String netName = net.describe(false);
				if (netName.length() == 0) continue;
				netNames.add(netName);
			}
			Collections.sort(netNames);
			for(String s: netNames) model.addElement(s);

			// add "Done" and "Cancel" buttons
			JButton done = new JButton("Done");
			gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 1;
			gridBagConstraints.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(done, gridBagConstraints);
			done.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { closeDialog(); }
			});

			JButton cancel = new JButton("Cancel");
			gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 2;
			gridBagConstraints.gridy = 1;
			gridBagConstraints.insets = new Insets(4, 4, 4, 4);
			getContentPane().add(cancel, gridBagConstraints);
			cancel.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent evt) { list.clearSelection();  closeDialog(); }
			});

			pack();
			setVisible(true);
		}

		public String getSelectedNetwork()
		{
			String name = (String)list.getSelectedValue();
			if (name == null) return null;
			return name;
		}
	}

	/****************************** MOVE SELECTED OBJECTS ******************************/

	/**
	 * Method to move the selected geometry by (dX, dY).
	 */
	public static void manyMove(double dX, double dY)
	{
        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (wf == null) return;
        Cell cell = wf.getContent().getCell();
        Highlighter highlighter = wf.getContent().getHighlighter();
        if (highlighter == null) return;

        List<Highlight> highlighted = highlighter.getHighlights();

        // prevent mixing cell-center and non-cell-center
        int nonCellCenterCount = 0;
        Highlight cellCenterHighlight = null;
        List<ElectricObject> highlightedEObjs = new ArrayList<ElectricObject>();
        for(Highlight h : highlighted)
        {
        	if (!h.isHighlightEOBJ()) continue;
        	ElectricObject eObj = h.getElectricObject();
    		highlightedEObjs.add(eObj);
        	if (eObj instanceof NodeInst)
        	{
        		NodeInst ni = (NodeInst)eObj;
        		if (Generic.isCellCenter(ni)) cellCenterHighlight = h; else
        			nonCellCenterCount++;
        	} else nonCellCenterCount++;
        }
        if (cellCenterHighlight != null && nonCellCenterCount != 0)
        {
        	System.out.println("Cannot move the Cell-center along with other objects.  Cell-center will not be moved.");
        	highlighted.remove(cellCenterHighlight);
        }
        List<DisplayedText> highlightedText = highlighter.getHighlightedText(true);

		if (!highlightedEObjs.isEmpty() || !highlightedText.isEmpty())
            new CircuitChangeJobs.ManyMove(cell, highlightedEObjs, highlightedText, dX, dY);
	}

    /****************************** CHANGE CELL EXPANSION ******************************/

    /**
     * Method to change the expansion of the selected instances.
     * @param unExpand true to unexpand the instances (draw them as black boxes),
     * false to expand them (draw their contents).
     * @param amount the number of levels of hierarchy to expand/unexpand.
     * If negative, prompt for an amount.
     */
    public static void DoExpandCommands(boolean unExpand, int amount)
    {
        if (amount < 0)
        {
            Object obj = JOptionPane.showInputDialog("Number of levels to " + (unExpand ? "unexpand" : "expand"), "1");
            if (obj != null) amount = TextUtils.atoi((String)obj);
            if (amount <= 0) return;
        }

        List<NodeInst> list = MenuCommands.getSelectedNodes();
        if (list.isEmpty()) return;
        Cell cell = list.get(0).getParent();
        for(NodeInst ni : list)
        {
            assert ni.getParent() == cell;
            if (!ni.isCellInstance()) continue;

            if (false)
            {
            	// new speedup patch from AM
				HashSet<Cell> hm = new HashSet<Cell>();
				cell.getCellsToDepth(hm, amount);
				for(Cell parent : hm)
				{
					for (ImmutableNodeInst subN : parent.backup().cellRevision.nodes)
					{
						if (!unExpand) {
							parent.setExpanded(subN.nodeId, true);
							//doExpand(cell, ni.getD(), amount, 0);
						} else if (ni.isExpanded()) {
							parent.setExpanded(subN.nodeId, false);
							//doUnExpand(cell, ni.getD(), amount);
						}
					}
				}
            } else
            {
            	// original code
            	if (!unExpand)
            		doExpand(cell, ni.getD(), amount, 0);
            	else if (ni.isExpanded())
            		doUnExpand(cell, ni.getD(), amount);
            }
        }
        EditWindow.expansionChanged(cell);
        EditWindow.clearSubCellCache();
        EditWindow.repaintAllContents();
    }

    /**
     * Method to recursively expand the cell "ni" by "amount" levels.
     * "sofar" is the number of levels that this has already been expanded.
     */
    private static void doExpand(Cell parent, ImmutableNodeInst n, int amount, int sofar)
    {
        if (!parent.isExpanded(n.nodeId))
        {
            // expanded the cell
            parent.setExpanded(n.nodeId, true);

            // if depth limit reached, quit
            if (++sofar >= amount) return;
        }

        // explore insides of this one
        if (!n.isCellInstance()) return;
        EDatabase database = parent.getDatabase();
        Cell cell = (Cell)n.protoId.inDatabase(database);
        for (ImmutableNodeInst subN: cell.backup().cellRevision.nodes)
        {
            if (!subN.isCellInstance()) continue;

            // ignore recursive references (showing icon in contents)
            Cell subCell = (Cell)subN.protoId.inDatabase(database);
            if (subCell.isIconOf(cell)) continue;
            doExpand(cell, subN, amount, sofar);
        }
    }

    private static int doUnExpand(Cell parent, ImmutableNodeInst n, int amount)
    {
        if (!parent.isExpanded(n.nodeId)) return 0;

        if (!n.isCellInstance()) return 1;
        EDatabase database = parent.getDatabase();
        int depth = 0;
        Cell cell = (Cell)n.protoId.inDatabase(database);
        for (ImmutableNodeInst subN: cell.backup().cellRevision.nodes)
        {
            if (!subN.isCellInstance()) continue;

            // ignore recursive references (showing icon in contents)
            Cell subCell = (Cell)subN.protoId.inDatabase(database);
            if (subCell.isIconOf(cell)) continue;
            if (cell.isExpanded(subN.nodeId))
                depth = Math.max(depth, doUnExpand(cell, subN, amount));
        }
        if (depth < amount) {
            parent.setExpanded(n.nodeId, false);
        }
        return depth+1;
    }

	/****************************** LIBRARY CHANGES ******************************/

	/**
	 * Method to implement the "List Libraries" command.
	 */
	public static void listLibrariesCommand()
	{
		System.out.println("----- Libraries: -----");
		int k = 0;
		for (Library lib : Library.getVisibleLibraries())
		{
			if (lib.isHidden()) continue;
			StringBuffer infstr = new StringBuffer();
			infstr.append(lib.getName());
			if (lib.isChanged())
			{
				infstr.append("*");
				k++;
			}
			if (lib.getLibFile() != null)
				infstr.append(" (disk file: " + lib.getLibFile() + ")");
			System.out.println(infstr.toString());

			// see if there are dependencies
			Set<String> dummyLibs = new HashSet<String>();
			Set<Library> markedLibs = new HashSet<Library>();
			for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
			{
				Cell cell = cIt.next();
				for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
				{
					NodeInst ni = nIt.next();
					if (!ni.isCellInstance()) continue;
					Cell subCell = (Cell)ni.getProto();
					String pt = subCell.getVarValue(LibraryFiles.IO_TRUE_LIBRARY, String.class);
					if (pt != null)
					{
						dummyLibs.add(pt);
					}
					markedLibs.add(subCell.getLibrary());
				}
			}
			for(Iterator<Library> lIt = Library.getLibraries(); lIt.hasNext(); )
			{
				Library oLib = lIt.next();
				if (oLib == lib) continue;
				if (!markedLibs.contains(oLib)) continue;
				System.out.println("   Makes use of cells in " + oLib);
				infstr = new StringBuffer();
				infstr.append("      These cells make reference to that library:");
				for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
				{
					Cell cell = cIt.next();
					boolean found = false;
					for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
					{
						NodeInst ni = nIt.next();
						if (!ni.isCellInstance()) continue;
						Cell subCell = (Cell)ni.getProto();
						if (subCell.getLibrary() == oLib) { found = true;   break; }
					}
					if (found) infstr.append(" " + cell.noLibDescribe());
				}
				System.out.println(infstr.toString());
			}
			for(String dummyLibName : dummyLibs)
			{
				System.out.println("   Has dummy cells that should be in library " + dummyLibName);
				infstr = new StringBuffer();
				infstr.append("      Instances of these dummy cells are in:");
				for(Iterator<Cell> cIt = lib.getCells(); cIt.hasNext(); )
				{
					Cell cell = cIt.next();
					boolean found = false;
					for(Iterator<NodeInst> nIt = cell.getNodes(); nIt.hasNext(); )
					{
						NodeInst ni = nIt.next();
						if (!ni.isCellInstance()) continue;
						Cell subCell = (Cell)ni.getProto();
						String libName = subCell.getVarValue(LibraryFiles.IO_TRUE_LIBRARY, String.class);
						if (dummyLibName.equals(libName)) { found = true;   break; }
					}
					if (found) infstr.append(" " + cell.noLibDescribe());
				}
				System.out.println(infstr.toString());
			}
		}
		if (k != 0) System.out.println("   (* means library has changed)");
	}

	/**
	 * Method to implement the "Rename Current Technology" command.
	 */
	public static void renameCurrentTechnology()
	{
		Technology tech = Technology.getCurrent();
		String techName = tech.getTechName();
		String val = JOptionPane.showInputDialog("New Name of Technology " + techName + ":", techName);
		if (val == null) return;
		if (val.equals(techName)) return;
		new CircuitChangeJobs.RenameTechnology(tech, val);
	}

	/**
	 * Method to implement the "Rename Library" command.
	 */
	public static void renameLibrary(Library lib)
	{
		String val = JOptionPane.showInputDialog("New Name of Library:", lib.getName());
		if (val == null) return;
		new CircuitChangeJobs.RenameLibrary(lib, val);
	}

	/**
	 * Method to implement the "Repair Libraries" command.
	 */
	public static void checkAndRepairCommand(boolean repair)
	{
		new CircuitChangeJobs.CheckAndRepairJob(repair);
	}

	/**
	 * Method to implement the "Find unused library files" command.
	 */
	public static void findUnusedLibraryFiles()
	{
		// first make a list of all directories associated with the libraries in memory
		Map<String,List<String>> directories = new HashMap<String,List<String>>();
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library lib = it.next();
			if (lib.isHidden()) continue;
			if (!lib.isFromDisk()) continue;
			String dirName = lib.getLibFile().getFile();
			File file = TextUtils.getFile(lib.getLibFile());
			if (file == null) continue; // in case of libraries included in jar package
			String fileName = file.getName();

			// ignore if a library file
			URL libFile = LibFile.getLibFile(fileName);
			if (libFile != null && libFile.getFile().equals(dirName)) continue;

			// crop file name from directory path
			int crop = dirName.lastIndexOf(fileName);
			if (crop > 0) dirName = dirName.substring(0, crop);
			List<String> filesInDir = directories.get(dirName);
			if (filesInDir == null)
			{
				filesInDir = new ArrayList<String>();
				directories.put(dirName, filesInDir);
			}
			filesInDir.add(fileName);
		}

		if (directories.size() == 0)
		{
			System.out.println("Before running this command, you must read some libraries from disk.");
			System.out.println("The command will then examine the disk to see if there are other libraries that were not read in");
			return;
		}
		for(String dirName : directories.keySet())
		{
			File dirFile = new File(dirName);
			boolean firstInDir = true;
			if (dirFile.isDirectory())
			{
				List<String> filesInDir = directories.get(dirName);
				String [] files = dirFile.list();
				if (files == null) continue;
				for(int i=0; i<files.length; i++)
				{
					String file = files[i].toLowerCase();
					if (file.endsWith(".jelib") || file.endsWith(".elib") || file.endsWith(".delib"))
					{
						if (filesInDir.contains(files[i])) continue;
						if (firstInDir) System.out.println("Directory " + dirName + " has these unused library files:");
						firstInDir = false;
						System.out.println("   " + files[i]);
					}
				}
			}
		}
	}

    /****************************** DELETE UNUSED NODES ******************************/

    /**
     * Method to remove nodes containing metal layers that have been disabled.
     * If library is null, then check all existing libraries
     */
    public static void removeUnusedLayers(Library lib)
    {
        // kick the delete job
//        new RemoveUnusedLayers(lib);
    }
}
