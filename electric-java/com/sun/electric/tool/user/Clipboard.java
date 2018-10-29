/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Clipboard.java
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
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Cell.CellGroup;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.NodeInst.ExpansionState;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.user.ui.ClickZoomWireListener;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.MessagesWindow;
import com.sun.electric.tool.user.ui.TextWindow;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.EDimension;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

/**
 * Class for managing the circuitry clipboard (for copy and paste).
 */
public class Clipboard //implements ClipboardOwner
{
    /** The Clipboard CellId. */
    public static final String CLIPBOARD_LIBRAY_NAME = "Clipboard!!";
    public static final String CLIPBOARD_CELL_NAME =  "Clipboard!!;1{}";
    public static final int CLIPBOARD_CELL_INDEX = 0;

	/** the amount that the last node moved */			private static double    lastDupX = 10, lastDupY = 10;

    /**
	 * There is only one instance of this object (just one clipboard).
	 */
	private Clipboard() {}

    /**
	 * Returns a printable version of this Clipboard.
	 * @return a printable version of this Clipboard.
	 */
	public String toString() { return "Clipboard"; }

	// this is really only for debugging
	public static void editClipboard()
	{
		EditWindow wnd = EditWindow.getCurrent();
		wnd.setCell(getClipCell(), VarContext.globalContext, null);
	}

	/**
	 * Method to copy the selected objects to the clipboard.
	 */
	public static void copy()
	{
        // Clear text buffer
        TextUtils.setTextOnClipboard(null);

        // is this the messages window?
        MessagesWindow mw = MessagesWindow.getFocusOwner();
        if (mw != null)
        {
        	mw.copyText(false, false);
        	return;
        }

        // if current window is text window, copy from it
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf != null && wf.getContent() instanceof TextWindow)
		{
			TextWindow tw = (TextWindow)wf.getContent();
			tw.copy();
			return;
		}

		// get the edit window
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;
		Highlighter highlighter = wnd.getHighlighter();
		List<Geometric> highlightedGeoms = highlighter.getHighlightedEObjs(true, true);
		List<DisplayedText> highlightedText = highlighter.getHighlightedText(true);
		if (highlightedGeoms.size() == 0 && highlightedText.size() == 0)
		{
			System.out.println("First select objects to copy");
			return;
		}

		// special case: if one text object is selected, copy its text to the system clipboard
		copySelectedText(highlightedText);

		// create the transformation for "down in place" copying
		FixpTransform inPlace = new FixpTransform();
		Orientation inPlaceOrient = Orientation.IDENT;
		if (wnd.isInPlaceEdit())
		{
			List<NodeInst> nodes = wnd.getInPlaceEditNodePath();
			for(NodeInst n : nodes)
			{
				Orientation o = n.getOrient().inverse();
				inPlaceOrient = o.concatenate(inPlaceOrient);
			}
			FixpTransform justRotation = inPlaceOrient.pureRotate();

			Rectangle2D pasteBounds = getPasteBounds(highlightedGeoms, highlightedText, wnd);
			FixpTransform untranslate = FixpTransform.getTranslateInstance(-pasteBounds.getCenterX(), -pasteBounds.getCenterY());
			FixpTransform retranslate = FixpTransform.getTranslateInstance(pasteBounds.getCenterX(), pasteBounds.getCenterY());
			inPlace.preConcatenate(untranslate);
			inPlace.preConcatenate(justRotation);
			inPlace.preConcatenate(retranslate);
		}

		// copy to Electric clipboard cell
		new CopyObjects(wnd.getCell(), highlightedGeoms, highlightedText, User.getAlignmentToGrid(),
			inPlace, inPlaceOrient);
	}

	/**
	 * Method to copy the selected objects to the clipboard and then delete them.
	 */
	public static void cut()
	{
        // is this the messages window?
        MessagesWindow mw = MessagesWindow.getFocusOwner();
        if (mw != null)
        {
        	mw.copyText(false, true);
        	return;
        }

        // if current window is text window, cut from it
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf != null && wf.getContent() instanceof TextWindow)
		{
			TextWindow tw = (TextWindow)wf.getContent();
			tw.cut();
			return;
		}

		// get the edit window
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;
		Highlighter highlighter = wnd.getHighlighter();
		List<Geometric> highlightedGeoms = highlighter.getHighlightedEObjs(true, true);
		List<DisplayedText> highlightedText = highlighter.getHighlightedText(true);
		if (highlightedGeoms.size() == 0 && highlightedText.size() == 0)
		{
			System.out.println("First select objects to cut");
			return;
		}
		highlighter.clear();
		highlighter.finished();

		// special case: if one text object is selected, copy its text to the system clipboard
		copySelectedText(highlightedText);

		// create the transformation for "down in place" copying
		FixpTransform inPlace = new FixpTransform();
		Orientation inPlaceOrient = Orientation.IDENT;
		if (wnd.isInPlaceEdit())
		{
			List<NodeInst> nodes = wnd.getInPlaceEditNodePath();
			for(NodeInst n : nodes)
			{
				Orientation o = n.getOrient().inverse();
				inPlaceOrient = o.concatenate(inPlaceOrient);
			}
			FixpTransform justRotation = inPlaceOrient.pureRotate();

			Rectangle2D pasteBounds = getPasteBounds(highlightedGeoms, highlightedText, wnd);
			FixpTransform untranslate = FixpTransform.getTranslateInstance(-pasteBounds.getCenterX(), -pasteBounds.getCenterY());
			FixpTransform retranslate = FixpTransform.getTranslateInstance(pasteBounds.getCenterX(), pasteBounds.getCenterY());
			inPlace.preConcatenate(untranslate);
			inPlace.preConcatenate(justRotation);
			inPlace.preConcatenate(retranslate);
		}

		// cut from Electric, copy to clipboard cell
		new CutObjects(wnd.getCell(), highlightedGeoms, highlightedText, User.getAlignmentToGrid(),
			User.isReconstructArcsAndExportsToDeletedCells(), inPlace, inPlaceOrient);
	}

	/**
	 * Method to paste the clipboard back into the current cell.
	 */
	public static void paste()
	{
        // is this the messages window?
        MessagesWindow mw = MessagesWindow.getFocusOwner();
        if (mw != null)
        {
        	mw.pasteText();
        	return;
        }

        // if current window is text window, paste to it
		WindowFrame wf = WindowFrame.getCurrentWindowFrame();
		if (wf != null && wf.getContent() instanceof TextWindow)
		{
			TextWindow tw = (TextWindow)wf.getContent();
			tw.paste();
			return;
		}

        // Get text put in the clipboard but using variables.
        String extraText = TextUtils.getTextOnClipboard();

        // get objects to paste
		int nTotal = 0, aTotal = 0, vTotal = 0;
        Cell clipCell = getClipCell();
		if (clipCell != null)
		{
			nTotal = clipCell.getNumNodes();
			aTotal = clipCell.getNumArcs();
			vTotal = clipCell.getNumVariables();
            if (clipCell.getVar(User.FRAME_LAST_CHANGED_BY) !=  null) vTotal--; // discount this variable since it should not be copied.
		}

		// find out where the paste is going
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
        {
            System.out.println("No place to paste");
            return;
        }
		Highlighter highlighter = wnd.getHighlighter();
		Cell parent = wnd.getCell();

        int total = nTotal + aTotal + vTotal;
		if (total == 0)
		{
            if (extraText != null)
            {
                List<DisplayedText> highlightedText = highlighter.getHighlightedText(true);
                new PasteSpecialText(highlightedText, extraText, parent);
            }
            else
                System.out.println("Nothing in the clipboard to paste");
			return;
		}

		// special case of pasting on top of selected objects
		List<Geometric> geoms = highlighter.getHighlightedEObjs(true, true);
		if (geoms.size() > 0)
		{
			// can only paste a single object onto selection
			if (nTotal == 2 && aTotal == 1)
			{
				ArcInst ai = clipCell.getArcs().next();
				NodeInst niHead = ai.getHeadPortInst().getNodeInst();
				NodeInst niTail = ai.getTailPortInst().getNodeInst();
				Iterator<NodeInst> nIt = clipCell.getNodes();
				NodeInst ni1 = nIt.next();
				NodeInst ni2 = nIt.next();
				if ((ni1 == niHead && ni2 == niTail) ||
					(ni1 == niTail && ni2 == niHead)) nTotal = 0;
				total = nTotal + aTotal;
			}
			if (total > 1)
			{
				System.out.println("Can only paste a single object on top of selected objects");
				return;
			}
			for(Geometric geom : geoms)
			{
				if (geom instanceof NodeInst && nTotal == 1)
				{
					NodeInst ni = (NodeInst)geom;
					new PasteNodeToNode(ni, clipCell.getNodes().next());
				} else if (geom instanceof ArcInst)
				{
					ArcInst ai = (ArcInst)geom;
                    if (aTotal == 1)
                        new PasteArcToArc(ai, clipCell.getArcs().next());
                    else
                        new PasteSpecialText(Arrays.asList(new DisplayedText(ai, ArcInst.ARC_NAME)), extraText, parent);
//                        new ai.setName(extraText);
                }
			}
			return;
		}

		// make list of things to paste
		List<Geometric> geomList = new ArrayList<Geometric>();
		for(Iterator<NodeInst> it = clipCell.getNodes(); it.hasNext(); )
			geomList.add(it.next());
		for(Iterator<ArcInst> it = clipCell.getArcs(); it.hasNext(); )
			geomList.add(it.next());
		List<DisplayedText> textList = new ArrayList<DisplayedText>();
		for (Iterator<Variable> it = clipCell.getVariables(); it.hasNext(); )
		{
			Variable var = it.next();
			if (!var.isDisplay()) continue;
			textList.add(new DisplayedText(clipCell, var.getKey()));
		}

		if (geomList.size() == 0 && textList.size() == 0) return;

		// create the transformation for "down in place" pasting
		FixpTransform inPlace = new FixpTransform();
		Orientation inPlaceOrient = Orientation.IDENT;
		if (wnd.isInPlaceEdit())
		{
			List<NodeInst> nodes = wnd.getInPlaceEditNodePath();
			for(NodeInst n : nodes)
			{
				Orientation o = n.getOrient();
				inPlaceOrient = inPlaceOrient.concatenate(o);
			}
			FixpTransform justRotation = inPlaceOrient.pureRotate();

			Rectangle2D pasteBounds = getPasteBounds(geomList, textList, wnd);
			FixpTransform untranslate = FixpTransform.getTranslateInstance(-pasteBounds.getCenterX(), -pasteBounds.getCenterY());
			FixpTransform retranslate = FixpTransform.getTranslateInstance(pasteBounds.getCenterX(), pasteBounds.getCenterY());
			inPlace.preConcatenate(untranslate);
			inPlace.preConcatenate(justRotation);
			inPlace.preConcatenate(retranslate);
		}

		boolean convertSchLay = false;
		if (aTotal == 0 && User.isConvertSchematicLayoutWhenPasting()) convertSchLay = true;
		if (User.isMoveAfterDuplicate())
		{
			WindowFrame.ElectricEventListener currentListener = WindowFrame.getListener();
			WindowFrame.setListener(new PasteListener(clipCell, wnd, geomList, textList, currentListener,
				inPlace, inPlaceOrient, false, convertSchLay));
		} else if (User.isDuplicateInPlace()) {
			new PasteObjects(clipCell, parent, geomList, textList, 0, 0,
				User.getAlignmentToGrid(), User.isDupCopiesExports(), User.isIncrementRightmostIndex(),
				User.isArcsAutoIncremented(), convertSchLay, inPlace, inPlaceOrient);
        } else
		{
			new PasteObjects(clipCell, parent, geomList, textList, lastDupX, lastDupY,
				User.getAlignmentToGrid(), User.isDupCopiesExports(), User.isIncrementRightmostIndex(),
				User.isArcsAutoIncremented(), convertSchLay, inPlace, inPlaceOrient);
		}
	}

	/**
	 * Method to duplicate the selected objects.
	 */
	public static void duplicate()
	{
		// see what is highlighted
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;
		Highlighter highlighter = wnd.getHighlighter();
		List<Geometric> geomList = highlighter.getHighlightedEObjs(true, true);
		List<DisplayedText> textList = highlighter.getHighlightedText(true);
		if (geomList.size() == 0 && textList.size() == 0)
		{
			System.out.println("First select objects to duplicate");
			return;
		}

		// do duplication
		if (User.isMoveAfterDuplicate())
		{
			WindowFrame.ElectricEventListener currentListener = WindowFrame.getListener();
			WindowFrame.setListener(new PasteListener(wnd.getCell(), wnd, geomList, textList, currentListener, null, null, true, false));
		} else if (User.isDuplicateInPlace()) {
            lastDupX = 0;
            lastDupY = 0;
			new DuplicateObjects(wnd.getCell(), geomList, textList, User.getAlignmentToGrid());
		} else
		{
			new DuplicateObjects(wnd.getCell(), geomList, textList, User.getAlignmentToGrid());
		}
	}

	/**
	 * Helper method to copy any selected text to the system-wide clipboard.
	 */
	private static void copySelectedText(List<DisplayedText> highlightedText)
	{
		// must be one text selected
		if (highlightedText.size() != 1) return;

		// get the text
		DisplayedText dt = highlightedText.get(0);
		ElectricObject eObj = dt.getElectricObject();
		Variable.Key varKey = dt.getVariableKey();
        String selected = null;

        if (varKey == ArcInst.ARC_NAME)
        {
            selected = ((ArcInst)eObj).getName();
        }
        else if (varKey == NodeInst.NODE_NAME || varKey == NodeInst.NODE_PROTO)
        {
            selected = ((NodeInst)eObj).getName();
        }
        else if (varKey == Export.EXPORT_NAME)
        {
            selected = ((Export)eObj).getName();
        }
        else
        {
            Variable var = eObj.getParameterOrVariable(varKey);
            if (var == null) return;
            selected = var.describe(-1);
        }
        if (selected == null) return;

		// put the text in the clipboard
        TextUtils.setTextOnClipboard(selected);
	}

    public static void databaseChanged(DatabaseChangeEvent e) {
    }

	/****************************** CHANGE JOBS ******************************/

	private static class CopyObjects extends Job
	{
		private List<Geometric> highlightedGeoms;
		private List<DisplayedText> highlightedText;
		private EDimension alignment;
		private FixpTransform inPlace;
		private Orientation inPlaceOrient;
        private boolean isDupCopiesExports = User.isDupCopiesExports();
        private boolean isIncrementRightmostIndex = User.isIncrementRightmostIndex();
        private boolean isArcsAutoIncremented = User.isArcsAutoIncremented();
        private ExpansionState expansionState;
        private List<NodeInst> nodesToExpand;

		protected CopyObjects(Cell cell, List<Geometric> highlightedGeoms, List<DisplayedText> highlightedText,
			EDimension alignment, FixpTransform inPlace, Orientation inPlaceOrient)
		{
			super("Copy", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.highlightedGeoms = highlightedGeoms;
			this.highlightedText = highlightedText;
			this.alignment = alignment;
			this.inPlace = inPlace;
			this.inPlaceOrient = inPlaceOrient;
			this.nodesToExpand =  new ArrayList<NodeInst>();
			this.expansionState = new ExpansionState(cell, ExpansionState.JUSTTHISCELL);
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			// remove contents of clipboard
			clear();

			// copy objects to clipboard
	        copyListToCell(getClipCell(), highlightedGeoms, highlightedText, null, null, new Point2D.Double(),
				isDupCopiesExports, isIncrementRightmostIndex, isArcsAutoIncremented, false,
				alignment, inPlace, inPlaceOrient, expansionState, nodesToExpand, getEditingPreferences());
            fieldVariableChanged("nodesToExpand");
			return true;
		}

        @Override
		public void terminateOK()
		{
			for(NodeInst ni : nodesToExpand)
				ni.setExpanded(true);
		}
	}

	private static class CutObjects extends Job
	{
		private Cell cell;
		private List<Geometric> geomList;
		private List<DisplayedText> textList;
		private EDimension alignment;
		private boolean reconstructArcsAndExports;
		private FixpTransform inPlace;
		private Orientation inPlaceOrient;
		private List<Geometric> thingsToHighlight;
        private boolean isDupCopiesExports = User.isDupCopiesExports();
        private boolean isIncrementRightmostIndex = User.isIncrementRightmostIndex();
        private boolean isArcsAutoIncremented = User.isArcsAutoIncremented();
        private ExpansionState expansionState;
        private List<NodeInst> nodesToExpand;

		protected CutObjects(Cell cell, List<Geometric> geomList, List<DisplayedText> textList, EDimension alignment,
			boolean reconstructArcsAndExports, FixpTransform inPlace, Orientation inPlaceOrient)
		{
			super("Cut", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.geomList = geomList;
			this.textList = textList;
			this.alignment = alignment;
			this.reconstructArcsAndExports = reconstructArcsAndExports;
			this.inPlace = inPlace;
			this.inPlaceOrient = inPlaceOrient;
			this.expansionState = new ExpansionState(cell, ExpansionState.JUSTTHISCELL);
			this.nodesToExpand = new ArrayList<NodeInst>();
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			// remove contents of clipboard
			clear();

			// make sure deletion is allowed
			if (CircuitChangeJobs.cantEdit(cell, null, true, false, true) != 0) return false;
			for(Geometric geom : geomList)
			{
				if (geom instanceof NodeInst)
				{
					int errorCode = CircuitChangeJobs.cantEdit(cell, (NodeInst)geom, true, false, true);
					if (errorCode < 0) return false;
					if (errorCode > 0) continue;
				}
			}

			// copy objects to clipboard
	        copyListToCell(getClipCell(), geomList, textList, null, null, new Point2D.Double(),
				isDupCopiesExports, isIncrementRightmostIndex, isArcsAutoIncremented, false,
				alignment, inPlace, inPlaceOrient, expansionState, nodesToExpand, ep);
			fieldVariableChanged("nodesToExpand");

			// and delete the original objects
			Set<ElectricObject> stuffToHighlight = new HashSet<ElectricObject>();
			CircuitChangeJobs.eraseObjectsInList(cell, geomList, reconstructArcsAndExports, stuffToHighlight, ep);
			thingsToHighlight = new ArrayList<Geometric>();
			for(ElectricObject eObj : stuffToHighlight)
			{
				if (eObj instanceof ArcInst)
				{
					ArcInst ai = (ArcInst)eObj;
					thingsToHighlight.add(ai);
				} else if (eObj instanceof Export)
				{
					Export e = (Export)eObj;
					thingsToHighlight.add(e.getOriginalPort().getNodeInst());
				}
			}
			fieldVariableChanged("thingsToHighlight");

			// kill exports and variables on cells
			for(DisplayedText dt : textList)
			{
				// deleting variable on object
				Variable.Key key = dt.getVariableKey();
				ElectricObject eobj = dt.getElectricObject();
				if (key == NodeInst.NODE_NAME)
				{
					// deleting the name of a node
					NodeInst ni = (NodeInst)eobj;
					ni.setName(null);
					ni.move(0, 0);
				} else if (key == ArcInst.ARC_NAME)
				{
					// deleting the name of an arc
					ArcInst ai = (ArcInst)eobj;
					ai.setName(null, ep);
					ai.modify(0, 0, 0, 0);
				} else if (key == Export.EXPORT_NAME)
				{
					// deleting the name of an export
					Export pp = (Export)eobj;
					pp.kill();
				} else
				{
					// deleting a variable
					if (eobj.isParam(key)) {
                        if (eobj instanceof Cell)
                        {
                        	CellGroup cg = ((Cell)eobj).getCellGroup();
                        	if (cg != null) cg.delParam((Variable.AttrKey)key);
                        } else if (eobj instanceof NodeInst)
                            ((NodeInst)eobj).delParameter(key);
                    } else {
						eobj.delVar(key);
                    }
				}
			}
			return true;
		}

        @Override
		public void terminateOK()
		{
			// expand requested nodes
			for(NodeInst ni : nodesToExpand)
				ni.setExpanded(true);

			// remove highlighting, show only reconstructed objects
			UserInterface ui = Job.getUserInterface();
			EditWindow_ wnd = ui.getCurrentEditWindow_();
			if (wnd != null)
			{
				wnd.clearHighlighting();
				if (thingsToHighlight != null)
				{
					for(Geometric geom: thingsToHighlight)
						wnd.addElectricObject(geom, cell);
				}
				wnd.finishedHighlighting();
			}
		}
	}

	private static class DuplicateObjects extends Job
	{
		private Cell cell;
		private List<Geometric> geomList, newGeomList;
		private List<DisplayedText> textList, newTextList;
		private boolean dupCopiesExports, fromRight, autoIncrementArcs;
		private EDimension alignment;
		private ExpansionState expansionState;
		private List<NodeInst> nodesToExpand;

        protected DuplicateObjects(Cell cell, List<Geometric> geomList, List<DisplayedText> textList, EDimension alignment)
		{
			super("Duplicate", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
			this.geomList = geomList;
			this.textList = textList;
			this.alignment = alignment;
			dupCopiesExports = User.isDupCopiesExports();
			fromRight = User.isIncrementRightmostIndex();
			autoIncrementArcs = User.isArcsAutoIncremented();
			this.expansionState = new ExpansionState(cell, ExpansionState.JUSTTHISCELL);
			this.nodesToExpand = new ArrayList<NodeInst>();
            startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			// copy objects to clipboard
			newGeomList = new ArrayList<Geometric>();
			newTextList = new ArrayList<DisplayedText>();
			copyListToCell(cell, geomList, textList, newGeomList, newTextList, new Point2D.Double(lastDupX, lastDupY),
				dupCopiesExports, fromRight, autoIncrementArcs, false, alignment, null, null, expansionState, nodesToExpand, ep);
			fieldVariableChanged("newGeomList");
			fieldVariableChanged("newTextList");
			fieldVariableChanged("nodesToExpand");
			return true;
		}

        @Override
		public void terminateOK()
		{
			// highlight the copy
			showCopiedObjects(newGeomList, newTextList);

			// expand requested nodes
			for(NodeInst ni : nodesToExpand)
				ni.setExpanded(true);
		}
	}

	private static class PasteArcToArc extends Job
	{
		private ArcInst src, dst, newArc;

		protected PasteArcToArc(ArcInst dst, ArcInst src)
		{
			super("Paste Arc to Arc", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.src = src;
			this.dst = dst;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			// make sure pasting is allowed
			if (CircuitChangeJobs.cantEdit(dst.getParent(), null, true, false, true) != 0) return false;

			newArc = pasteArcToArc(dst, src, getEditingPreferences());
			if (newArc == null) System.out.println("Nothing was pasted"); else
				fieldVariableChanged("newArc");
			return true;
		}

        @Override
		public void terminateOK()
		{
			if (newArc != null)
			{
				EditWindow wnd = EditWindow.getCurrent();
				if (wnd != null)
				{
					Highlighter highlighter = wnd.getHighlighter();
					if (highlighter != null)
					{
						highlighter.clear();
						highlighter.addElectricObject(newArc, newArc.getParent());
						highlighter.finished();
					}
				}
			}
		}
	}

	private static class PasteNodeToNode extends Job
	{
		private NodeInst src, dst, newNode;

		protected PasteNodeToNode(NodeInst dst, NodeInst src)
		{
			super("Paste Node to Node", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.src = src;
			this.dst = dst;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			// make sure pasting is allowed
			if (CircuitChangeJobs.cantEdit(dst.getParent(), null, true, false, true) != 0) return false;

			newNode = pasteNodeToNode(dst, src, getEditingPreferences());
			if (newNode == null) System.out.println("Nothing was pasted"); else
				fieldVariableChanged("newNode");
			return true;
		}

        @Override
		public void terminateOK()
		{
			if (newNode != null)
			{
				newNode.setExpanded(src.isExpanded());
				EditWindow wnd = EditWindow.getCurrent();
				if (wnd != null)
				{
					Highlighter highlighter = wnd.getHighlighter();
					if (highlighter != null)
					{
						highlighter.clear();
						highlighter.addElectricObject(newNode, newNode.getParent());
						highlighter.finished();
					}
				}
			}
		}
	}

	private static class PasteObjects extends Job
	{
		private Cell toCell;
		private List<Geometric> geomList, newGeomList;
		private List<DisplayedText> textList, newTextList;
		private double dX, dY;
		private EDimension alignment;
		private boolean copyExports, fromRight, uniqueArcs, convertSchLay;
		private FixpTransform inPlace;
		private Orientation inPlaceOrient;
		private ExpansionState expansionState;
		private List<NodeInst> nodesToExpand;

		protected PasteObjects(Cell fromCell, Cell toCell, List<Geometric> geomList, List<DisplayedText> textList, double dX, double dY,
                               EDimension alignment, boolean copyExports, boolean fromRight, boolean uniqueArcs,
                               boolean convertSchLay, FixpTransform inPlace, Orientation inPlaceOrient)
		{
			super("Paste", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.toCell = toCell;
			this.geomList = geomList;
			this.textList = textList;
			this.dX = dX;
			this.dY = dY;
			this.alignment = alignment;
			this.copyExports = copyExports;
			this.fromRight = fromRight;
			this.uniqueArcs = uniqueArcs;
			this.convertSchLay = convertSchLay;
			this.inPlace = inPlace;
			this.inPlaceOrient = inPlaceOrient;
			this.expansionState = new ExpansionState(fromCell, ExpansionState.JUSTTHISCELL);
			this.nodesToExpand = new ArrayList<NodeInst>();
            startJob();
		}

        @Override
		public boolean doIt() throws JobException
		{
			// make sure pasting is allowed
			if (CircuitChangeJobs.cantEdit(toCell, null, true, false, true) != 0) return false;

			// paste them into the current cell
			newGeomList = new ArrayList<Geometric>();
			newTextList = new ArrayList<DisplayedText>();
			copyListToCell(toCell, geomList, textList, newGeomList, newTextList, new Point2D.Double(dX, dY),
                copyExports, fromRight, uniqueArcs, convertSchLay, alignment, inPlace, inPlaceOrient,
                expansionState, nodesToExpand, getEditingPreferences());
			fieldVariableChanged("newGeomList");
			fieldVariableChanged("newTextList");
			fieldVariableChanged("nodesToExpand");
			return true;
		}

        @Override
		public void terminateOK()
		{
			// highlight the copy
			showCopiedObjects(newGeomList, newTextList);

			// expand requested nodes
			for(NodeInst ni : nodesToExpand)
				ni.setExpanded(true);
			if (nodesToExpand.size() > 0)
				EditWindow.repaintAllContents();
		}
	}

    private static class PasteSpecialText extends Job
	{
        private List<DisplayedText> textList;
        private String newText;
        private Cell cell;

        protected PasteSpecialText(List<DisplayedText> tList, String newT, Cell c)
        {
            super("Paste Text", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.textList = tList;
            this.newText = newT;
            this.cell = c;
            startJob();
        }

        @Override
		public boolean doIt() throws JobException
		{
            EditingPreferences ep = getEditingPreferences();
			// make sure pasting is allowed
			if (CircuitChangeJobs.cantEdit(cell, null, true, false, true) != 0) return false;

			// paste them into the current cell
            for (DisplayedText obj : textList)
            {
                Variable.Key key = obj.getVariableKey();
                ElectricObject eObj = obj.getElectricObject();
                if (key == ArcInst.ARC_NAME)
                {
                    ((ArcInst)eObj).setName(newText, ep);
                }
                else if (key == NodeInst.NODE_NAME || key == NodeInst.NODE_PROTO)
                {
                    ((NodeInst)eObj).setName(newText);
                }
                else if (key == Export.EXPORT_NAME)
                {
                    Export ex = (Export)eObj;
                    ex.rename(newText);
                }
            }
			return true;
		}
	}

    /****************************** CHANGE JOB SUPPORT ******************************/

    public static CellId getClipCellId() {
        return IdManager.stdIdManager.getCellId(CLIPBOARD_CELL_INDEX);
    }

	/**
	 * Method to clear the clipboard.
	 */
	private static Cell getClipCell()
	{
        return Job.getUserInterface().getDatabase().getCell(getClipCellId());
	}

	/**
	 * Method to clear the contents of the clipboard.
	 */
	public static void clear()
	{
        Cell clipCell = getClipCell();
        if (clipCell == null) return;

        // delete all arcs in the clipboard
		List<ArcInst> arcsToDelete = new ArrayList<ArcInst>();
		for(Iterator<ArcInst> it = clipCell.getArcs(); it.hasNext(); )
			arcsToDelete.add(it.next());
		for(ArcInst ai : arcsToDelete)
		{
			ai.kill();
		}

		// delete all exports in the clipboard
		Set<Export> exportsToDelete = new HashSet<Export>();
		for(Iterator<Export> it = clipCell.getExports(); it.hasNext(); )
			exportsToDelete.add(it.next());
		clipCell.killExports(exportsToDelete);

		// delete all nodes in the clipboard
		Set<NodeInst> nodesToDelete = new HashSet<NodeInst>();
		for(Iterator<NodeInst> it = clipCell.getNodes(); it.hasNext(); )
			nodesToDelete.add(it.next());
		clipCell.killNodes(nodesToDelete);

		// Delete all variables
		for(Iterator<Variable> it = clipCell.getParameters(); it.hasNext(); ) {
			Variable var = it.next();
			if (clipCell.getCellGroup() != null)
				clipCell.getCellGroup().delParam((Variable.AttrKey)var.getKey());
		}
		for(Iterator<Variable> it = clipCell.getVariables(); it.hasNext(); )
		{
			Variable var = it.next();
			clipCell.delVar(var.getKey());
		}
	}

	/**
	 * Method to copy the list of Geometrics to a new Cell.
	 * @param toCell the destination cell of the Geometrics.
	 * @param geomList the list of Geometrics to copy.
	 * @param textList the list of text to copy.
	 * @param newGeomList the list of Geometrics that were created.
	 * @param newTextList the list of text objects that were created.
	 * @param delta an offset for all of the copied Geometrics.
	 * @param copyExports true to copy exports.
	 * @param fromRight true to increment the rightmost index of multidimensional arrays.
	 * @param uniqueArcs true to generate unique arc names.
	 * @param convertSchLay true to convert between schematic and layout cells as needed by the current cell.
	 * @param alignment the grid alignment to use (0 for none).
	 * @param inPlace the transformation to use which accounts for "down in place" editing.
	 * @param inPlaceOrient the orientation to use which accounts for "down in place" editing.
     * @param ep EditingPreferneces
	 * @return the last NodeInst that was created.
	 */
	public static NodeInst copyListToCell(Cell toCell, List<Geometric> geomList, List<DisplayedText> textList,
                                          List<Geometric> newGeomList, List<DisplayedText> newTextList, Point2D delta,
                                          boolean copyExports, boolean fromRight, boolean uniqueArcs, boolean convertSchLay,
                                          EDimension alignment, FixpTransform inPlace, Orientation inPlaceOrient,
                                          ExpansionState expansionState, List<NodeInst> nodesToExpand, EditingPreferences ep)
	{
		// make a list of all objects to be copied (includes end points of arcs)
		List<NodeInst> theNodes = new ArrayList<NodeInst>();
		List<ArcInst> theArcs = new ArrayList<ArcInst>();

        if (copyExports)
        {
        	// copy the geometry list since it might be augmented
        	List<Geometric> internalGeomList = new ArrayList<Geometric>();
        	for(Geometric geom : geomList) internalGeomList.add(geom);
        	geomList = internalGeomList;

        	for(DisplayedText dt : textList)
            {
                ElectricObject eObj = dt.getElectricObject();
                if (eObj instanceof Export)
                {
                    // add the object exported to the list
                    Export e = (Export)eObj;
                    NodeInst ni = e.getOriginalPort().getNodeInst();
                    if (!geomList.contains(ni))
                        geomList.add(ni);
                }
            }
        }

        for (Geometric geom : geomList)
		{
			if (geom instanceof NodeInst)
			{
				if (!theNodes.contains(geom)) theNodes.add((NodeInst)geom);
			}
			if (geom instanceof ArcInst)
			{
				ArcInst ai = (ArcInst)geom;
				theArcs.add(ai);
				NodeInst head = ai.getHeadPortInst().getNodeInst();
				NodeInst tail = ai.getTailPortInst().getNodeInst();
				if (!theNodes.contains(head)) theNodes.add(head);
				if (!theNodes.contains(tail)) theNodes.add(tail);
			}
		}
		if (theNodes.isEmpty() && textList.isEmpty()) return null;

		// check for recursion
		for(NodeInst ni : theNodes)
		{
			if (!ni.isCellInstance()) continue;
			Cell niCell = (Cell)ni.getProto();
			if (Cell.isInstantiationRecursive(niCell, toCell))
			{
				System.out.println("Cannot: that would be recursive (" +
					toCell.describe(false) + " is beneath " + niCell.describe(false) + ")");
				return null;
			}
		}

		DBMath.gridAlign(delta, alignment);
		double dX = delta.getX();
		double dY = delta.getY();

		// sort the nodes by name
		Collections.sort(theNodes);

		// create the new nodes
		NodeInst lastCreatedNode = null;
		Map<NodeInst,NodeInst> newNodes = new HashMap<NodeInst,NodeInst>();
		List<Export> reExports = new ArrayList<Export>();
		for(NodeInst ni : theNodes)
		{
			double width = ni.getXSize();
			double height = ni.getYSize();
			if (Generic.isCellCenter(ni) && toCell.alreadyCellCenter()) continue;

			NodeProto makeProto = ni.getProto();
			// convert cell instances to the proper view if requested
			if (ni.isCellInstance() && convertSchLay)
			{
				Cell otherCell = findAlternate((Cell)makeProto, toCell);
				if (otherCell != makeProto)
				{
					makeProto = otherCell;
					width = otherCell.getDefWidth();
					height = otherCell.getDefHeight();
				}
			}

			String name = null;
			if (ni.isUsernamed())
				name = ElectricObject.uniqueObjectName(ni.getName(), toCell, NodeInst.class, false, fromRight);
			EPoint point = EPoint.fromLambda(ni.getAnchorCenterX()+dX, ni.getAnchorCenterY()+dY);
			Orientation orient = ni.getOrient();
			if (inPlace != null)
			{
				Point2D dst = new Point2D.Double(0, 0);
				inPlace.transform(new Point2D.Double(ni.getAnchorCenterX(), ni.getAnchorCenterY()), dst);
				point = EPoint.fromLambda(dst.getX()+dX, dst.getY()+dY);
				orient = orient.concatenate(inPlaceOrient);
			}
			NodeInst newNi = NodeInst.newInstance(makeProto, ep, point, width, height,
				toCell, orient, name, ni.getTechSpecific());
			if (newNi == null)
			{
				System.out.println("Cannot create node");
				return lastCreatedNode;
			}
			if (expansionState.isExpanded(ni))
				nodesToExpand.add(newNi);
			newNi.copyStateBits(ni);
			newNi.copyTextDescriptorFrom(ni, NodeInst.NODE_PROTO);
			newNi.copyTextDescriptorFrom(ni, NodeInst.NODE_NAME);
			newNi.copyVarsFrom(ni);

            // adjust if there is trace information
            for (Iterator<Variable> it = ni.getVariables(); it.hasNext();)
            {
            	Variable var = it.next();
            	if (var.getKey() == NodeInst.TRACE)
            		newNi.setTraceRelative((EPoint[])var.getObject(), point, Orientation.IDENT);
            }

			newNodes.put(ni, newNi);
			if (newGeomList != null) newGeomList.add(newNi);
			lastCreatedNode = newNi;

			// copy the ports, too
			if (copyExports)
			{
				for(Iterator<Export> eit = ni.getExports(); eit.hasNext(); )
				{
					Export e = eit.next();
					if (makeProto != ni.getProto())
						e = e.findEquivalent((Cell)makeProto);
					if (e != null) reExports.add(e);
				}
			}
		}
		if (copyExports)
		{
			// sort list of original Exports and make list of PortInsts on new nodes
			Map<PortInst,Export> originalExports = new HashMap<PortInst,Export>();
			Collections.sort(reExports, new ExportChanges.ExportSortedByBusIndex());
			List<PortInst> reExpThese = new ArrayList<PortInst>();
			for(Export e : reExports)
			{
				PortInst pi = e.getOriginalPort();
				NodeInst newNi = newNodes.get(pi.getNodeInst());
				PortInst newPi = newNi.findPortInstFromEquivalentProto(pi.getPortProto());
				reExpThese.add(newPi);
				originalExports.put(newPi, e);
			}
			ExportChanges.reExportPorts(toCell, reExpThese, false, true, true, false, fromRight, originalExports, ep);
		}

		Map<ArcInst,ArcInst> newArcs = new HashMap<ArcInst,ArcInst>();
		if (theArcs.size() > 0)
		{
			// sort the arcs by name
			Collections.sort(theArcs);

			// for associating old names with new names
			Map<String,String> newArcNames = new HashMap<String,String>();

			FixpTransform fixOffset = null;
			if (inPlaceOrient != null) fixOffset = inPlaceOrient.pureRotate();

			// create the new arcs
			for(ArcInst ai : theArcs)
			{
				PortInst oldHeadPi = ai.getHeadPortInst();
				NodeInst headNi = newNodes.get(oldHeadPi.getNodeInst());
				PortInst headPi = headNi.findPortInstFromEquivalentProto(oldHeadPi.getPortProto());
				EPoint headP = oldHeadPi.getCenter();
				double headDX = ai.getHeadLocation().getX() - headP.getX();
				double headDY = ai.getHeadLocation().getY() - headP.getY();

				PortInst oldTailPi = ai.getTailPortInst();
				NodeInst tailNi = newNodes.get(oldTailPi.getNodeInst());
				PortInst tailPi = tailNi.findPortInstFromEquivalentProto(oldTailPi.getPortProto());
				EPoint tailP = oldTailPi.getCenter();
				double tailDX = ai.getTailLocation().getX() - tailP.getX();
				double tailDY = ai.getTailLocation().getY() - tailP.getY();

				// adjust offset if down-in-place
				if (fixOffset != null)
				{
					Point2D result = new Point2D.Double(0, 0);
					fixOffset.transform(new Point2D.Double(headDX, headDY), result);
					headDX = result.getX();
					headDY = result.getY();
					fixOffset.transform(new Point2D.Double(tailDX, tailDY), result);
					tailDX = result.getX();
					tailDY = result.getY();
				}

				String name = null;
				if (ai.isUsernamed())
				{
					name = ai.getName();
					if (uniqueArcs)
					{
						String newName = newArcNames.get(name);
						if (newName == null)
						{
							newName = ElectricObject.uniqueObjectName(name, toCell, ArcInst.class, false, fromRight);
							newArcNames.put(name, newName);
						}
						name = newName;
					}
				}
				headP = EPoint.fromLambda(headPi.getCenter().getX() + headDX, headPi.getCenter().getY() + headDY);
				tailP = EPoint.fromLambda(tailPi.getCenter().getX() + tailDX, tailPi.getCenter().getY() + tailDY);
				ArcInst newAr = ArcInst.newInstanceBase(ai.getProto(), ep, ai.getLambdaBaseWidth(),
					headPi, tailPi, headP, tailP, name, ai.getAngle());
				if (newAr == null)
				{
					System.out.println("Cannot create arc");
					return lastCreatedNode;
				}
				newAr.copyPropertiesFrom(ai);
				newArcs.put(ai, newAr);
				if (newGeomList != null) newGeomList.add(newAr);
			}
		}

		// copy variables on cells
		int numDuplicates = 0;
		for(DisplayedText dt : textList)
		{
			ElectricObject eObj = dt.getElectricObject();
			if (!(eObj instanceof Cell)) continue;
			Variable.Key varKey = dt.getVariableKey();
			Variable var = eObj.getParameterOrVariable(varKey);
			double xP = var.getTextDescriptor().getXOff();
			double yP = var.getTextDescriptor().getYOff();
			if (toCell.getVar(varKey) != null) numDuplicates++;
            Variable newVar = Variable.newInstance(varKey, var.getObject(), var.getTextDescriptor().withOff(xP+dX, yP+dY));
            if (var.getTextDescriptor().isParam())
            {
            	if (toCell.getCellGroup() != null)
            		toCell.getCellGroup().addParam(newVar);
            } else
                toCell.addVar(newVar);
			if (newTextList != null) newTextList.add(new DisplayedText(toCell, varKey));
		}
		if (numDuplicates != 0) System.out.println("WARNING: New cell attributes replace old ones");
		return lastCreatedNode;
	}

	private static Cell findAlternate(Cell cell, Cell destination)
	{
		View desiredView = destination.getView();
		if (desiredView == View.SCHEMATIC) desiredView = View.ICON;
		if (cell.getView() != desiredView)
		{
			Cell otherCell = cell.otherView(desiredView);
			if (otherCell != null) return otherCell;
		}
		return cell;
	}

	private static void showCopiedObjects(List<Geometric> newGeomList, List<DisplayedText> newTextList)
	{
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd != null)
		{
			Cell cell = wnd.getCell();
			Highlighter highlighter = wnd.getHighlighter();
			highlighter.clear();
			for(Geometric geom : newGeomList)
			{
				if (geom instanceof NodeInst)
				{
					NodeInst ni = (NodeInst)geom;

					// special case for displayable text on invisible pins
					if (ni.isInvisiblePinWithText())
					{
						for(Iterator<Variable> vIt = ni.getVariables(); vIt.hasNext(); )
						{
							Variable var = vIt.next();
							if (!var.isDisplay()) continue;
							highlighter.addText(ni, cell, var.getKey());
						}
						continue;
					}
				}
				highlighter.addElectricObject(geom, cell);
			}
			for(DisplayedText dt : newTextList)
			{
				highlighter.addText(dt.getElectricObject(), cell, dt.getVariableKey());
			}
			highlighter.finished();
		}
	}

	/**
	 * Method to "paste" node onto another node, making them the same.
	 * @param destNode the destination node (which will be deleted and replaced).
	 * @param srcNode the source node.
     * @param ep EditingPreferences
	 * Returns the address of the new destination node (null on error).
	 */
	private static NodeInst pasteNodeToNode(NodeInst destNode, NodeInst srcNode, EditingPreferences ep)
	{
		destNode = CircuitChangeJobs.replaceNodeInst(destNode, srcNode.getProto(), srcNode.getFunction(), true, false, false, ep);
		if (destNode == null) return null;

//		destNode.setExpanded(srcNode.isExpanded());

		if (!destNode.isCellInstance() && !srcNode.isCellInstance()) {
			if (srcNode.getProto().getTechnology() == destNode.getProto().getTechnology()) {
				Technology tech = srcNode.getProto().getTechnology();
				destNode.setPrimitiveFunction(srcNode.getFunction());
			}
		}

		// make the sizes the same if they are primitives
		if (!destNode.isCellInstance())
		{
			double dX = srcNode.getXSize() - destNode.getXSize();
			double dY = srcNode.getYSize() - destNode.getYSize();
			if (dX != 0 || dY != 0)
			{
				destNode.resize(dX, dY);
			}
		}

		// remove parameters that are not on the pasted object
        for(Iterator<Variable> it = destNode.getDefinedParameters(); it.hasNext(); )
        {
            Variable destParam = it.next();
            Variable.Key key = destParam.getKey();
            if (!srcNode.isDefinedParameter(key))
                destNode.delParameter(key);
        }

		// remove variables that are not on the pasted object
		boolean checkAgain = true;
		while (checkAgain)
		{
			checkAgain = false;
			for(Iterator<Variable> it = destNode.getVariables(); it.hasNext(); )
			{
				Variable destVar = it.next();
				Variable.Key key = destVar.getKey();
				Variable srcVar = srcNode.getVar(key);
				if (srcVar != null) continue;
				destNode.delVar(key);
				checkAgain = true;
				break;
			}
		}

		// make sure all variables are on the node
		destNode.copyVarsFrom(srcNode);

		// copy any special user bits
		destNode.copyStateBits(srcNode);
		destNode.setExpanded(false);
		destNode.clearLocked();

		return(destNode);
	}

	/**
	 * Method to paste one arc onto another.
	 * @param destArc the destination arc that will be replaced.
	 * @param srcArc the source arc that will replace it.
     * @param ep EditingPreferences
	 * @return the replaced arc (null on error).
	 */
	private static ArcInst pasteArcToArc(ArcInst destArc, ArcInst srcArc, EditingPreferences ep)
	{
		// make sure they have the same type
		if (destArc.getProto() != srcArc.getProto())
		{
			destArc = destArc.replace(srcArc.getProto(), ep);
			if (destArc == null) return null;
		}

		// make the widths the same
		destArc.setLambdaBaseWidth(srcArc.getLambdaBaseWidth());

		// remove variables that are not on the pasted object
		boolean checkAgain = true;
		while (checkAgain)
		{
			checkAgain = false;
			for(Iterator<Variable> it = destArc.getVariables(); it.hasNext(); )
			{
				Variable destVar = it.next();
				Variable.Key key = destVar.getKey();
				Variable srcVar = srcArc.getVar(key);
				if (srcVar != null) continue;
				destArc.delVar(key);
				checkAgain = true;
				break;
			}
		}

		// make sure all variables are on the arc
		for(Iterator<Variable> it = srcArc.getVariables(); it.hasNext(); )
		{
			Variable srcVar = it.next();
			Variable.Key key = srcVar.getKey();
			destArc.newVar(key, srcVar.getObject(), srcVar.getTextDescriptor());
		}

		// make sure the constraints and other userbits are the same
		destArc.copyPropertiesFrom(srcArc);
		return destArc;
	}

	/**
	 * Gets a boundary representing the paste bounds of the list of objects.
	 * The corners and center point of the bounds can be used as anchors
	 * when pasting the objects interactively. This is all done in database units.
	 * Note: you will likely want to grid align any points before using them.
	 * @param pasteList a list of Geometrics to paste
	 * @return a Rectangle2D that is the paste bounds.
	 */
	private static Rectangle2D getPasteBounds(List<Geometric> geomList, List<DisplayedText> textList, EditWindow wnd) {

		Point2D llcorner = null;
		Point2D urcorner = null;

		// figure out lower-left corner and upper-right corner of this collection of objects
		for(DisplayedText dt : textList)
		{
			Poly poly = dt.getElectricObject().computeTextPoly(wnd, dt.getVariableKey());
			Rectangle2D bounds = poly.getBounds2D();

			if (llcorner == null) {
				llcorner = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
				urcorner = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
				continue;
			}
			if (bounds.getMinX() < llcorner.getX()) llcorner.setLocation(bounds.getMinX(), llcorner.getY());
			if (bounds.getMinY() < llcorner.getY()) llcorner.setLocation(llcorner.getX(), bounds.getMinY());
			if (bounds.getMaxX() > urcorner.getX()) urcorner.setLocation(bounds.getMaxX(), urcorner.getY());
			if (bounds.getMaxY() > urcorner.getY()) urcorner.setLocation(urcorner.getX(), bounds.getMaxY());
		}
		for(Geometric geom : geomList)
		{
			if (geom instanceof NodeInst)
			{
				NodeInst ni = (NodeInst)geom;
				Point2D pt = ni.getAnchorCenter();

				if (llcorner == null) {
					llcorner = new Point2D.Double(pt.getX(), pt.getY());
					urcorner = new Point2D.Double(pt.getX(), pt.getY());
					continue;
				}
				if (pt.getX() < llcorner.getX()) llcorner.setLocation(pt.getX(), llcorner.getY());
				if (pt.getY() < llcorner.getY()) llcorner.setLocation(llcorner.getX(), pt.getY());
				if (pt.getX() > urcorner.getX()) urcorner.setLocation(pt.getX(), urcorner.getY());
				if (pt.getY() > urcorner.getY()) urcorner.setLocation(urcorner.getX(), pt.getY());
			} else
			{
				ArcInst ai = (ArcInst)geom;
				Poly poly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.FILLED);
				Rectangle2D bounds = poly.getBounds2D();

				if (llcorner == null) {
					llcorner = new Point2D.Double(bounds.getMinX(), bounds.getMinY());
					urcorner = new Point2D.Double(bounds.getMaxX(), bounds.getMaxY());
					continue;
				}
				if (bounds.getMinX() < llcorner.getX()) llcorner.setLocation(bounds.getMinX(), llcorner.getY());
				if (bounds.getMinY() < llcorner.getY()) llcorner.setLocation(llcorner.getX(), bounds.getMinY());
				if (bounds.getMaxX() > urcorner.getX()) urcorner.setLocation(bounds.getMaxX(), urcorner.getY());
				if (bounds.getMaxY() > urcorner.getY()) urcorner.setLocation(urcorner.getX(), bounds.getMaxY());
			}
		}

		// figure bounds
		double width = urcorner.getX() - llcorner.getX();
		double height = urcorner.getY() - llcorner.getY();
		Rectangle2D bounds = new Rectangle2D.Double(llcorner.getX(), llcorner.getY(), width, height);
		return bounds;
	}

	/****************************** PASTE LISTENER ******************************/

	/**
	 * Class to handle the interactive drag after a paste.
	 */
	private static class PasteListener implements WindowFrame.ElectricEventListener
	{
		private Cell fromCell;
		private EditWindow wnd;
		private List<Geometric> geomList;
		private List<DisplayedText> textList;
		private WindowFrame.ElectricEventListener currentListener;
		private Rectangle2D pasteBounds;
		private double translateX;
		private double translateY;
		private Point2D lastMouseDB;				// last point where mouse was (in database units)
		private JPopupMenu popup;
		private FixpTransform inPlace;
		private Orientation inPlaceOrient;
		private boolean convertSchLay;

		/**
		 * Create a new paste listener
		 * @param wnd Controlling window
		 * @param pasteList list of objects to paste
		 * @param currentListener listener to restore when done
		 */
		private PasteListener(Cell fromCell, EditWindow wnd, List<Geometric> geomList, List<DisplayedText> textList,
			WindowFrame.ElectricEventListener currentListener, FixpTransform inPlace, Orientation inPlaceOrient, boolean dup, boolean convertSchLay)
		{
			this.fromCell = fromCell;
			this.wnd = wnd;
			this.geomList = geomList;
			this.textList = textList;
			this.currentListener = currentListener;
			this.inPlace = inPlace;
			this.inPlaceOrient = inPlaceOrient;
			this.pasteBounds = getPasteBounds(geomList, textList, wnd);
			this.convertSchLay = convertSchLay;
			translateX = translateY = 0;

			initPopup();

			// get starting point from current mouse location
			Point2D mouse = ClickZoomWireListener.theOne.getLastMouse();
			Point2D mouseDB = wnd.screenToDatabase((int)mouse.getX(), (int)mouse.getY());
			if (dup)
			{
				pasteBounds.setRect(pasteBounds.getMinX()+mouseDB.getX()-pasteBounds.getCenterX(),
					pasteBounds.getMinY()+mouseDB.getY()-pasteBounds.getCenterY(),
					pasteBounds.getWidth(), pasteBounds.getHeight());
			}
			Point2D delta = getDelta(mouseDB, false);

			wnd.getHighlighter().pushHighlight();
			showList(delta);
		}

		/**
		 * Gets grid-aligned delta translation for nodes based on mouse location
		 * @param mouseDB the location of the mouse
		 * @param orthogonal if the translation is orthogonal only
		 * @return a grid-aligned delta
		 */
		private Point2D getDelta(Point2D mouseDB, boolean orthogonal)
		{
			// mouseDB == null if you press arrow keys before placing the new copy
			if (mouseDB == null) return null;
			EDimension alignment = User.getAlignmentToGrid();
			DBMath.gridAlign(mouseDB, alignment);

			// this is the point on the clipboard cell that will be pasted at the mouse location
			Point2D refPastePoint = new Point2D.Double(pasteBounds.getCenterX() + translateX,
													   pasteBounds.getCenterY() + translateY);

			double deltaX = mouseDB.getX() - refPastePoint.getX();
			double deltaY = mouseDB.getY() - refPastePoint.getY();

            // if orthogonal is true, convert to orthogonal
			if (orthogonal)
			{
                // Attempt to fix bug #1748
//                double distanceX = deltaX;
//                double distanceY = deltaY;
//
//                // deciding direction for the move
//                if (lastMouseDB != null)
//                {
//                    distanceX = mouseDB.getX() - lastMouseDB.getX();
//                    distanceY = mouseDB.getY() - lastMouseDB.getY();
//                }

				// If the mouse is within the X and Y extent of the object use X and Y only
				// If the mouse is not confined to either extent then use the 45 degree rule
				// Arguably we can change it to x degree rule where x is defined by the ratio of the widths
				if (mouseDB.getX() > pasteBounds.getMinX() && mouseDB.getX() < pasteBounds.getMaxX()) {
					deltaX = 0;
				} else if (mouseDB.getY() > pasteBounds.getMinY() && mouseDB.getY() < pasteBounds.getMaxY()) {
					deltaY = 0;
				} else {
					// only use delta in direction that has larger delta
					if (Math.abs(deltaX) > Math.abs(deltaY))
						deltaY = 0;
					else
						deltaX = 0;
				}

            }

			// this is now a delta, not a point
			refPastePoint.setLocation(deltaX, deltaY);
			DBMath.gridAlign(refPastePoint, alignment);
			return refPastePoint;
		}

		/**
		 * Show the objects to paste with the anchor point at 'mouseDB'
		 * @param delta the translation for the highlights
		 */
		private void showList(Point2D delta)
		{
			// if delta==null, problems to get mouseDB pointer
			if (delta == null) return;

			// find offset of highlights
			double oX = delta.getX();
			double oY = delta.getY();

			Cell cell = wnd.getCell();
            EditingPreferences ep = wnd.getEditingPreferences();
			Highlighter highlighter = wnd.getHighlighter();
			highlighter.clear();
			for(Geometric geom : geomList)
			{
				if (geom instanceof ArcInst)
				{
					ArcInst ai = (ArcInst)geom;
					Poly poly = ai.makeLambdaPoly(ai.getGridBaseWidth(), Poly.Type.CLOSED);
					if (inPlace != null) poly.transform(inPlace);
					Point2D [] points = poly.getPoints();
					showPoints(points, oX, oY, cell, highlighter);
					continue;
				}

				NodeInst ni = (NodeInst)geom;
				if (ni.isInvisiblePinWithText())
				{
					// find text on the invisible pin
					boolean found = false;
					for(Iterator<Variable> vIt = ni.getVariables(); vIt.hasNext(); )
					{
						Variable var = vIt.next();
						if (var.isDisplay())
						{
							Point2D [] points = Highlighter.describeHighlightText(wnd, geom, var.getKey());
							if (inPlace != null) inPlace.transform(points, 0, points, 0, points.length);
							showPoints(points, oX, oY, cell, highlighter);
							found = true;
							break;
						}
					}
					if (found) continue;
				}

				// convert cell instances to the proper view if requested
				if (ni.isCellInstance() && convertSchLay)
				{
					Cell otherCell = findAlternate((Cell)ni.getProto(), cell);
					if (otherCell != ni.getProto())
					{
						ni = NodeInst.makeDummyInstance(otherCell, ep, ni.getAnchorCenter(),
							otherCell.getDefWidth(), otherCell.getDefHeight(), ni.getOrient());
					}
				}

				Poly poly = ni.getBaseShape();
				if (inPlace != null) poly.transform(inPlace);
				showPoints(poly.getPoints(), oX, oY, cell, highlighter);
			}
			if (textList.size() > 0)
			{
				// show location of copied text
				double wid = 10;
				double pX = oX + pasteBounds.getCenterX() + translateX;
				double pY = oY + pasteBounds.getCenterY() + translateY;
				highlighter.addLine(new Point2D.Double(pX-wid, pY), new Point2D.Double(pX+wid, pY), cell);
			}

			// show delta from original
			Rectangle2D bounds = wnd.getDisplayedBounds();
			highlighter.addMessage(cell, "("+(int)oX+","+(int)oY+")",
					new Point2D.Double(bounds.getCenterX(),bounds.getCenterY()));

			// also draw arrow if user has moved highlights off the screen
			double halfWidth = 0.5*pasteBounds.getWidth();
			double halfHeight = 0.5*pasteBounds.getHeight();
			if (Math.abs(translateX) > halfWidth || Math.abs(translateY) > halfHeight)
			{
				Rectangle2D transBounds = new Rectangle2D.Double(pasteBounds.getX()+oX, pasteBounds.getY()+oY,
					pasteBounds.getWidth(), pasteBounds.getHeight());
				Poly p = new Poly(transBounds);
				if (inPlace != null) p.transform(inPlace);
				Point2D endPoint = p.closestPoint(lastMouseDB);

				// draw arrow
				highlighter.addLine(lastMouseDB, endPoint, cell);
				int angle = GenMath.figureAngle(lastMouseDB, endPoint);
				angle += 1800;
				int angleOfArrow = 300;		// 30 degrees
				int backAngle1 = angle - angleOfArrow;
				int backAngle2 = angle + angleOfArrow;
				Point2D p1 = new Point2D.Double(endPoint.getX() + DBMath.cos(backAngle1), endPoint.getY() + DBMath.sin(backAngle1));
				Point2D p2 = new Point2D.Double(endPoint.getX() + DBMath.cos(backAngle2), endPoint.getY() + DBMath.sin(backAngle2));
				highlighter.addLine(endPoint, p1, cell);
				highlighter.addLine(endPoint, p2, cell);
			}
			highlighter.finished();
		}

		private void showPoints(Point2D [] points, double oX, double oY, Cell cell, Highlighter highlighter)
		{
			for(int i=0; i<points.length; i++)
			{
				int lastI = i - 1;
				if (lastI < 0) lastI = points.length - 1;
				double fX = points[lastI].getX();
				double fY = points[lastI].getY();
				double tX = points[i].getX();
				double tY = points[i].getY();
				highlighter.addLine(new Point2D.Double(fX+oX, fY+oY), new Point2D.Double(tX+oX, tY+oY), cell);
			}
		}

		public void mousePressed(MouseEvent e)
		{
			if (e.isMetaDown()) {
				// right click
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		}

		public void mouseDragged(MouseEvent evt)
		{
			mouseMoved(evt);
		}

		public void mouseReleased(MouseEvent evt)
		{
			if (evt.isMetaDown()) {
				// right click
				return;
			}
			boolean ctrl = (evt.getModifiersEx()&MouseEvent.CTRL_DOWN_MASK) != 0;
			Point2D mouseDB = wnd.screenToDatabase(evt.getX(), evt.getY());
			Point2D delta = getDelta(mouseDB, ctrl);
			showList(delta);

			WindowFrame.setListener(currentListener);
			wnd.getHighlighter().popHighlight();
			Cell cell = WindowFrame.needCurCell();
			if (cell != null)
				new PasteObjects(fromCell, cell, geomList, textList, delta.getX(), delta.getY(),
					User.getAlignmentToGrid(), User.isDupCopiesExports(), User.isIncrementRightmostIndex(),
					User.isArcsAutoIncremented(), convertSchLay, inPlace, inPlaceOrient);
		}

		public void mouseMoved(MouseEvent evt)
		{
			boolean ctrl = (evt.getModifiersEx()&MouseEvent.CTRL_DOWN_MASK) != 0;
			Point2D mouseDB = wnd.screenToDatabase(evt.getX(), evt.getY());
			Point2D delta = getDelta(mouseDB, ctrl);
			lastMouseDB = mouseDB;
			showList(delta);

			wnd.repaint();
		}

		public void mouseClicked(MouseEvent evt) {}
		public void mouseEntered(MouseEvent evt) {}
		public void mouseExited(MouseEvent evt) {}
		public void mouseWheelMoved(MouseWheelEvent e) {}
		public void keyPressed(KeyEvent evt) {
			int chr = evt.getKeyCode();
			if (chr == KeyEvent.VK_ESCAPE) {
				// abort on ESC
				abort();
			}
			else if (chr == KeyEvent.VK_UP) {
				moveObjectsUp();
			}
			else if (chr == KeyEvent.VK_DOWN) {
				moveObjectsDown();
			}
			else if (chr == KeyEvent.VK_LEFT) {
				moveObjectsLeft();
			}
			else if (chr == KeyEvent.VK_RIGHT) {
				moveObjectsRight();
			}
		}
		public void keyReleased(KeyEvent e) {}
		public void keyTyped(KeyEvent e) {}
        public void databaseChanged(DatabaseChangeEvent e) {
            for (Iterator<Geometric> it = geomList.iterator(); it.hasNext(); ) {
                if (!it.next().isLinked()) {
                    it.remove();
                }
            }
            for (Iterator<DisplayedText> it = textList.iterator(); it.hasNext(); ) {
                if (!it.next().getElectricObject().isLinked()) {
                    it.remove();
                }
            }
        }

		private void abort() {
			wnd.getHighlighter().clear();
			wnd.getHighlighter().finished();
			WindowFrame.setListener(currentListener);
			wnd.repaint();
		}

		private void initPopup() {
			popup = new JPopupMenu();
			JMenuItem m;
			m = new JMenuItem("Move objects left");
			m.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0));
			m.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { moveObjectsLeft(); }
			});
			popup.add(m);

			m = new JMenuItem("Move objects right");
			m.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0));
			m.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { moveObjectsRight(); }
			});
			popup.add(m);

			m = new JMenuItem("Move objects up");
			m.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
			m.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { moveObjectsUp(); }
			});
			popup.add(m);

			m = new JMenuItem("Move objects down");
			m.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
			m.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { moveObjectsDown(); }
			});
			popup.add(m);

			m = new JMenuItem("Abort");
			m.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
			m.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) { abort(); }
			});
			popup.add(m);
		}

		private void moveObjectsLeft() {
			translateX += 0.5*pasteBounds.getWidth();
			Point2D delta = getDelta(lastMouseDB, false);
			showList(delta);
		}
		private void moveObjectsRight() {
			translateX -= 0.5*pasteBounds.getWidth();
			Point2D delta = getDelta(lastMouseDB, false);
			showList(delta);
		}
		private void moveObjectsUp() {
			translateY -= 0.5*pasteBounds.getHeight();
			Point2D delta = getDelta(lastMouseDB, false);
			showList(delta);
		}
		private void moveObjectsDown() {
			translateY += 0.5*pasteBounds.getHeight();
			Point2D delta = getDelta(lastMouseDB, false);
			showList(delta);
		}
	}

}
