/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CheckInJob.java
 * Project management tool: check a cell into the repository
 * Written by: Steven M. Rubin
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
package com.sun.electric.tool.project;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.util.math.MutableInteger;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Class to check a cell into the Project Management repository.
 */
public class CheckInJob extends Job
{
	private ProjectDB pdb;
	private Library lib;
	private HashMap<Cell,MutableInteger> cellsMarked;
	private String comment;
	private int backupScheme;
	private static String lastComment = null;

	/**
	 * Method to check the currently edited cell back into the repository.
	 */
	public static void checkInThisCell()
	{
		UserInterface ui = Job.getUserInterface();
		Cell cell = ui.needCurrentCell();
		if (cell == null) return;
		checkIn(cell);
	}

	/**
	 * Method to check a cell back into the repository.
	 * @param cell the Cell to check back in.
	 */
	public static void checkIn(Cell cell)
	{
		// make sure there is a valid user name and repository
		if (Users.needUserName()) return;
		if (Project.ensureRepository()) return;

		// determine all of the cells to write
		HashMap<Cell,MutableInteger> cellsMarked = markRelatedCells(cell);

		// check the cells for validity
		for(Cell aCell : cellsMarked.keySet())
		{
			MutableInteger mi = cellsMarked.get(aCell);
			if (mi.intValue() == 0) continue;

			// find this in the project file
			ProjectCell pc = Project.projectDB.findProjectCell(aCell);
			if (pc == null)
			{
				Job.getUserInterface().showErrorMessage("Cell " + aCell.describe(true) +
					" is not in the project.  Add it before checking it in or out.", "Check-In Error");
				return;
			}

			// see if it is available
			if (!pc.getOwner().equals(Project.getCurrentUserName()))
			{
				Job.getUserInterface().showErrorMessage("Cell " + aCell.describe(true) +
					"You cannot check-in " + aCell + " because it is checked out to '" + pc.getOwner() + "', not you.", "Check-In Error");
				return;
			}
		}

		String comment = Job.getUserInterface().askForInput("Reason for checking-in " + cell.describe(true),
			"Describe the Change", lastComment);
		if (comment == null) return;
		lastComment = comment;

		new CheckInJob(Project.projectDB, cell.getLibrary(), cellsMarked, comment);
	}

	private CheckInJob(ProjectDB pdb, Library lib, HashMap<Cell,MutableInteger> cellsMarked, String comment)
	{
		super("Check in cells", Project.getProjectTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
		this.pdb = pdb;
		this.lib = lib;
		this.cellsMarked = cellsMarked;
		this.comment = comment;
		this.backupScheme = IOTool.getBackupRedundancy();
		startJob();
	}

    @Override
	public boolean doIt() throws JobException
	{
        EditingPreferences ep = getEditingPreferences();
		ProjectLibrary pl = pdb.findProjectLibrary(lib);

		// lock access to the project files (throws JobException on error)
		pl.lockProjectFile();

		// prevent tools (including this one) from seeing the change
		Project.setChangeStatus(true);

		// check in the requested cells
		String cellNames = "";
		for(Cell cell : cellsMarked.keySet())
		{
			MutableInteger mi = cellsMarked.get(cell);
			if (mi.intValue() == 0) continue;
			if (cellNames.length() > 0) cellNames += ", ";
			cellNames += cell.describe(false);
		}

		String error = null;
		for(Cell cell : cellsMarked.keySet())
		{
			MutableInteger mi = cellsMarked.get(cell);
			if (mi.intValue() == 0) continue;

			// find this in the project file
			ProjectCell pc = pdb.findProjectCell(cell);
			if (pc == null)
			{
				error = "Cell " + cell.describe(true) + " is not in the project.  Add it before checking it in or out.";
			} else
			{
				// see if it is available
				if (!pc.getOwner().equals(Project.getCurrentUserName()))
				{
					error = "You cannot check-in " + cell + " because it is checked out to '" + pc.getOwner() + "', not you.";
				} else
				{
					// write the cell out there
					if (Project.writeCell(cell, pc, backupScheme, ep))		// CHANGES DATABASE
					{
						error = "Error writing " + cell;
					} else
					{
						pc.setOwner("");
						pc.setLastOwner(Project.getCurrentUserName());
						pc.setVersion(cell.getVersion());
						pc.setComment(comment);
						Project.markLocked(cell, true, ep);		// CHANGES DATABASE
						System.out.println("Cell " + cell.describe(true) + " checked in");
					}
				}
			}
		}

		// restore change broadcast
		Project.setChangeStatus(false);

		// relase project file lock
		pl.releaseProjectFileLock(true);
		if (error != null) throw new JobException(error);

		return true;
	}

	/**
	 * Method to determine what other cells need to be checked-in with a given Cell.
	 * @param cell the Cell being checked-in.
	 * @return a Map of Cells to check-in (if an entry in the map, associated with a Cell,
	 * is not null, that Cell should be checked-in).
	 */
	private static HashMap<Cell,MutableInteger> markRelatedCells(Cell cell)
	{
		// mark the cell to be checked-in
		HashMap<Cell,MutableInteger> cellsMarked1 = new HashMap<Cell,MutableInteger>();
		HashMap<Cell,MutableInteger> cellsMarked2 = new HashMap<Cell,MutableInteger>();
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library oLib = it.next();
			for(Iterator<Cell> cIt = oLib.getCells(); cIt.hasNext(); )
			{
				Cell oCell = cIt.next();
				cellsMarked1.put(oCell, new MutableInteger(0));
				cellsMarked2.put(oCell, new MutableInteger(0));
			}
		}
		MutableInteger mi = cellsMarked1.get(cell);
		mi.setValue(1);

		// look for cells above this one that must also be checked in
		mi = cellsMarked2.get(cell);
		mi.setValue(1);
		boolean propagated = true;
		while (propagated)
		{
			propagated = false;
			for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library oLib = it.next();
				for(Iterator<Cell> cIt = oLib.getCells(); cIt.hasNext(); )
				{
					Cell oCell = cIt.next();
					mi = cellsMarked2.get(oCell);
					if (mi.intValue() == 1)
					{
						propagated = true;
						mi.setValue(2);
						for(Iterator<NodeInst> nIt = oCell.getInstancesOf(); nIt.hasNext(); )
						{
							NodeInst ni = nIt.next();
							mi = cellsMarked2.get(ni.getParent());
							if (mi.intValue() == 0) mi.setValue(1);
						}
					}
				}
			}
		}
		mi = cellsMarked2.get(cell);
		mi.setValue(0);
		int total = 0;
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library oLib = it.next();
			for(Iterator<Cell> cIt = oLib.getCells(); cIt.hasNext(); )
			{
				Cell oCell = cIt.next();
				mi = cellsMarked2.get(oCell);
				if (mi.intValue() == 0) continue;
				String owner = Project.getCellOwner(oCell);
				if (owner.length() == 0) continue;
				if (owner.equals(Project.getCurrentUserName()))
				{
					mi = cellsMarked1.get(oCell);
					mi.setValue(1);
					total++;
				}
			}
		}

		// look for cells below this one that must also be checked in
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library oLib = it.next();
			for(Iterator<Cell> cIt = oLib.getCells(); cIt.hasNext(); )
			{
				Cell oCell = cIt.next();
				mi = cellsMarked2.get(oCell);
				mi.setValue(0);
			}
		}
		mi = cellsMarked2.get(cell);
		mi.setValue(1);
		propagated = true;
		while (propagated)
		{
			propagated = false;
			for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library oLib = it.next();
				for(Iterator<Cell> cIt = oLib.getCells(); cIt.hasNext(); )
				{
					Cell oCell = cIt.next();
					mi = cellsMarked2.get(oCell);
					if (mi.intValue() == 1)
					{
						propagated = true;
						mi.setValue(2);
						for(Iterator<NodeInst> nIt = oCell.getNodes(); nIt.hasNext(); )
						{
							NodeInst ni = nIt.next();
							if (!ni.isCellInstance()) continue;
							mi = cellsMarked2.get(ni.getProto());
							if (mi.intValue() == 0) mi.setValue(1);
						}
					}
				}
			}
		}
		mi = cellsMarked2.get(cell);
		mi.setValue(0);
		for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
		{
			Library oLib = it.next();
			for(Iterator<Cell> cIt = oLib.getCells(); cIt.hasNext(); )
			{
				Cell oCell = cIt.next();
				mi = cellsMarked2.get(oCell);
				if (mi.intValue() == 0) continue;
				String owner = Project.getCellOwner(oCell);
				if (owner.length() == 0) continue;
				if (owner.equals(Project.getCurrentUserName()))
				{
					mi = cellsMarked1.get(oCell);
					mi.setValue(1);
					total++;
				}
			}
		}

		// advise of additional cells that must be checked-in
		if (total > 0)
		{
			total = 0;
			StringBuffer infstr = new StringBuffer();
			for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
			{
				Library oLib = it.next();
				for(Iterator<Cell> cIt = oLib.getCells(); cIt.hasNext(); )
				{
					Cell oCell = cIt.next();
					mi = cellsMarked1.get(oCell);
					if (oCell == cell || mi.intValue() == 0) continue;
					if (total > 0) infstr.append(", ");
					infstr.append(oCell.describe(true));
					total++;
				}
			}
			System.out.println("Also checking in related cell(s): " + infstr.toString());
		}
		return cellsMarked1;
	}

}
