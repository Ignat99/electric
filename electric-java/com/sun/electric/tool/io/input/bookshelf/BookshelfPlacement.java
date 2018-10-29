/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: BookshelfPlacement.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.input.bookshelf;

import com.sun.electric.tool.io.input.bookshelf.BookshelfNodes.BookshelfNode;
import com.sun.electric.util.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

/**
 */
public class BookshelfPlacement
{
	private String plFile;

	public BookshelfPlacement(String plFile)
	{
		this.plFile = plFile;
	}

	public void parse()
		throws IOException
	{
		BufferedReader rin;
		try
		{
			File file = new File(plFile);
			FileReader freader = new FileReader(file);
			rin = new BufferedReader(freader);
		} catch (FileNotFoundException e) {
			System.out.println("ERROR: Cannot find Bookshelf Placement file: " + plFile);
			return;
		}

		// skip the first line
		rin.readLine();

		for(;;)
		{
			String line = rin.readLine();
			if (line == null) break;
			if (line.length() == 0) continue;
			if (line.charAt(0) == '#') continue;

			StringTokenizer tokenizer = new StringTokenizer(line, " ");
			int i = 0;
			String name = "";
			double x = 0;
			double y = 0;
			while (tokenizer.hasMoreTokens())
			{
				if (i == 0) {
					name = tokenizer.nextToken();
				} else if (i == 1) {
					x = TextUtils.atof(tokenizer.nextToken());
				} else if (i == 2) {
					y = TextUtils.atof(tokenizer.nextToken());
				} else {
					tokenizer.nextToken();
				}
				i++;
			}
			
			BookshelfNode bn = BookshelfNode.findNode(name);
			if (bn != null)
				bn.setLocation(x, y);
		}
	}
}
