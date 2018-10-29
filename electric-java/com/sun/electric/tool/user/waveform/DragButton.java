/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DragButton.java
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
package com.sun.electric.tool.user.waveform;

import java.awt.Cursor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.MouseEvent;

import javax.swing.JButton;

/**
 * Class to extend a JButton so that it is draggable.
 */
public class DragButton extends JButton implements DragGestureListener, DragSourceListener
{
	private DragSource dragSource;
	private int panelNumber;

	public DragButton(String s, int panelNumber)
	{
		super(s);
		this.panelNumber = panelNumber;
		setBorderPainted(false);
		dragSource = DragSource.getDefaultDragSource();
		dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, this);
	}

	public void dragGestureRecognized(DragGestureEvent e)
	{
		Cursor style = DragSource.DefaultMoveDrop;
		String command = "MOVEBUTTON";

		if ((e.getTriggerEvent().getModifiersEx()&MouseEvent.SHIFT_DOWN_MASK) != 0)
		style = DragSource.DefaultCopyDrop;
		command = "COPYBUTTON";

		// make the Transferable Object
		Transferable transferable = new StringSelection("PANEL " + panelNumber + " " + command+ " " + getText());

		// begin the drag
		dragSource.startDrag(e, style, transferable, this);
	}

	public void dragEnter(DragSourceDragEvent e) {}
	public void dragOver(DragSourceDragEvent e) {}
	public void dragExit(DragSourceEvent e) {}
	public void dragDropEnd(DragSourceDropEvent e) {}
	public void dropActionChanged (DragSourceDragEvent e) {}
}
