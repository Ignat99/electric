/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ERC.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.erc;

import com.sun.electric.tool.Tool;

/**
 * This is the Electrical Rule Checker tool.
 */
public class ERC extends Tool
{
	/** the ERC tool. */					protected static ERC tool = new ERC();

	/**
	 * The constructor sets up the ERC tool.
	 */
	private ERC()
	{
		super("erc");
	}

	/**
	 * Method to initialize the ERC tool.
	 */
    @Override
	public void init()
	{
	}

    /**
     * Method to retrieve singleton associated to ERC tool
     * @return the ERC tool.
     */
    public static ERC getERCTool() { return tool; }
}
