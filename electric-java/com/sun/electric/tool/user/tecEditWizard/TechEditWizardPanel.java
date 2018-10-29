/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: TechEditWizardPanel.java
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

import java.awt.*;

import javax.swing.JPanel;

/**
 * This class defines a superstructure for a panel in the Technology Creation Wizard dialog.
 */
public class TechEditWizardPanel extends EDialog
{
    protected TechEditWizard wizard;

	public TechEditWizardPanel(TechEditWizard parent, boolean modal)
	{
		super((Frame)parent.getOwner(), modal);
		wizard = parent;
	}

	/** return the panel to use for this Technology Creation Wizard tab. */
	public Component getComponent() { return null; }

	/** return the name of this Technology Creation Wizard tab. */
	public String getName() { return ""; }
    
	/**
	 * Method called when the panel is entered.
	 * Caches current values and displays them in the tab.
	 */
	public void init() {}

	/**
	 * Method called when the panel is left (switching to another or quitting dialog).
	 * Updates any changed fields in the tab.
	 */
	public void term() {}
}
