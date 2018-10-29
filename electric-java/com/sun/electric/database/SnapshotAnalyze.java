/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SnapshotAnalyze.java
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
package com.sun.electric.database;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.Variable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to analyze two different Snapshots and report the changes found.
 */
public class SnapshotAnalyze
{
	private Map<CellId,Set<ImmutableElectricObject>> added;
	private Map<CellId,Set<ImmutableElectricObject>> removed;
	private Set<CellId> portChanges;
	private Set<CellId> sizeChanges;
	private Set<CellId> cellVariableChanges;
	private List<CellId> deletedCells;
	private Snapshot newSnapshot;

	/**
	 * Constructor analyzes nodes and arcs that changed between two snapshots.
	 * @param oldSnapshot the old Snapshot.
	 * @param newSnapshot the new Snapshot.
	 */
	public SnapshotAnalyze(Snapshot oldSnapshot, Snapshot newSnapshot)
	{
		this.newSnapshot = newSnapshot;
		added = new HashMap<CellId,Set<ImmutableElectricObject>>();
		removed = new HashMap<CellId,Set<ImmutableElectricObject>>();
		portChanges = new HashSet<CellId>();
		sizeChanges = new HashSet<CellId>();
		cellVariableChanges = new HashSet<CellId>();
		deletedCells = new ArrayList<CellId>();

		// look at all cells that changed
		for (CellId cellId : newSnapshot.getChangedCells(oldSnapshot))
		{
			CellBackup oldBackup = oldSnapshot.getCell(cellId);
			CellBackup newBackup = newSnapshot.getCell(cellId);
			assert oldBackup != newBackup;

			// if the cell was created, ignore
			if (oldBackup == null) continue;

			// if the cell was deleted, add to list
			if (newBackup == null)
			{
				deletedCells.add(cellId);
				continue;
			}

			// cell changed: figure out how
			CellRevision oldRevision = oldBackup.cellRevision;
			CellRevision newRevision = newBackup.cellRevision;
			Set<ImmutableElectricObject> addedToCell = getAddedList(cellId);
			Set<ImmutableElectricObject> removedFromCell = getRemovedList(cellId);

			// see if the size changed
			if (!oldSnapshot.getCellBounds(cellId).equals(newSnapshot.getCellBounds(cellId)))
				sizeChanges.add(cellId);

			// look for variable differences
			Variable[] oldVars = oldRevision.d.getVars();
			Variable[] newVars = newRevision.d.getVars();
			if (oldVars.length != newVars.length) cellVariableChanges.add(cellId); else
			{
				for(int i=0; i<oldVars.length; i++)
				{
					if (oldVars[i].equals(newVars[i])) continue;
					cellVariableChanges.add(cellId);
					break;
				}
			}

			// update list of changed nodes
			int maxNodeId = Math.max(oldRevision.getMaxNodeId(), newRevision.getMaxNodeId());
			for(int nodeId=0; nodeId<=maxNodeId; nodeId++)
			{
				ImmutableNodeInst iNiOld = oldRevision.getNodeById(nodeId);
				ImmutableNodeInst iNiNew = newRevision.getNodeById(nodeId);
				if (iNiOld == iNiNew)
				{
					continue;
				}
				if (iNiOld != null) removedFromCell.add(iNiOld);
				if (iNiNew != null) addedToCell.add(iNiNew);
			}

			// update list of changed arcs
			int maxArcId = Math.max(oldRevision.getMaxArcId(), newRevision.getMaxArcId());
			for(int arcId=0; arcId<=maxArcId; arcId++)
			{
				ImmutableArcInst iAiOld = oldRevision.getArcById(arcId);
				ImmutableArcInst iAiNew = newRevision.getArcById(arcId);
				if (iAiOld == iAiNew) continue;
				if (iAiOld != null)
				{
					// old arc exists: was either modified or deleted
					removedFromCell.add(iAiOld);
					ImmutableNodeInst ini = oldRevision.getNodeById(iAiOld.headNodeId);
					if (ini != null) removedFromCell.add(ini);
					ini = oldRevision.getNodeById(iAiOld.tailNodeId);
					if (ini != null) removedFromCell.add(ini);
					if (iAiNew == null)
					{
						// arc was deleted
						ini = newRevision.getNodeById(iAiOld.headNodeId);
						if (ini != null) addedToCell.add(ini);
						ini = newRevision.getNodeById(iAiOld.tailNodeId);
						if (ini != null) addedToCell.add(ini);
					}
				}
				if (iAiNew != null)
				{
					// new arc exists: was either modified or created
					addedToCell.add(iAiNew);
					ImmutableNodeInst ini = newRevision.getNodeById(iAiNew.headNodeId);
					if (ini != null) addedToCell.add(ini);
					ini = newRevision.getNodeById(iAiNew.tailNodeId);
					if (ini != null) addedToCell.add(ini);

					// if old nodes exist, remove them for redraw
					ini = oldRevision.getNodeById(iAiNew.headNodeId);
					if (ini != null) removedFromCell.add(ini);
					ini = oldRevision.getNodeById(iAiNew.tailNodeId);
					if (ini != null) removedFromCell.add(ini);
				}
			}

			// update list of changed exports (but reduce it to changes to nodes)
			int maxExportChronIndex = Math.max(oldRevision.getMaxExportChronIndex(), newRevision.getMaxExportChronIndex());
			for(int chronIndex=0; chronIndex<=maxExportChronIndex; chronIndex++)
			{
				ExportId exportId = cellId.getPortId(chronIndex);
				ImmutableExport iExportOld = oldRevision.getExport(exportId);
				ImmutableExport iExportNew = newRevision.getExport(exportId);
				if (iExportOld == iExportNew) continue;

				portChanges.add(cellId);
				if (iExportOld != null)
				{
					CellBackup oldParentBackup = oldSnapshot.getCell(iExportOld.exportId.getParentId());
					CellRevision oldParentRevision = oldParentBackup.cellRevision;
					removedFromCell.add(oldParentRevision.getNodeById(iExportOld.originalNodeId));
					if (iExportNew == null)
					{
						// must repaint node that Export sits on, even if the export was deleted
						CellBackup newParentBackup = newSnapshot.getCell(iExportOld.exportId.getParentId());
						if (newParentBackup != null)
						{
							CellRevision newParentRevision = newParentBackup.cellRevision;
							ImmutableNodeInst ini = newParentRevision.getNodeById(iExportOld.originalNodeId);
							if (ini != null) addedToCell.add(ini);
						}
					}
				}
				if (iExportNew != null)
				{
					CellBackup newParentBackup = newSnapshot.getCell(iExportNew.exportId.getParentId());
					CellRevision newParentRevision = newParentBackup.cellRevision;
					addedToCell.add(newParentRevision.getNodeById(iExportNew.originalNodeId));
				}

				// figure out which instances need to be redrawn to account for the export change
				Set<CellId> changedParents = new HashSet<CellId>();
				for(int i=0; i<cellId.numUsagesOf(); i++)
				{
					CellUsage cu = cellId.getUsageOf(i);
					changedParents.add(cu.parentId);
				}
				for(CellId parentId : changedParents)
				{
					CellBackup oldParentBackup = oldSnapshot.getCell(parentId);
					CellBackup newParentBackup = newSnapshot.getCell(parentId);
					if (oldParentBackup != null)
					{
						Set<ImmutableElectricObject> removedList = getRemovedList(parentId);
						for (ImmutableNodeInst n : oldParentBackup.cellRevision.nodes)
							if (n.protoId == cellId) removedList.add(n);
					}
					if (newParentBackup != null)
					{
						Set<ImmutableElectricObject> addedList = getAddedList(parentId);
						for (ImmutableNodeInst n : newParentBackup.cellRevision.nodes)
							if (n.protoId == cellId) addedList.add(n);
					}
				}
		   	}

			// extra test for Export changes
			if (!portChanges.contains(cellId))
			{
				// see if any export is from a node that gets redrawn
				maxExportChronIndex = newRevision.getMaxExportChronIndex();
				for(int chronIndex=0; chronIndex<=maxExportChronIndex; chronIndex++)
				{
					ExportId exportId = cellId.getPortId(chronIndex);
					ImmutableExport iExportNew = newRevision.getExport(exportId);
					if (iExportNew != null)
					{
						ImmutableNodeInst ini = newRevision.getNodeById(iExportNew.originalNodeId);
						if (addedToCell.contains(ini))
						{
							portChanges.add(cellId);
							break;
						}
					}
				}
			}
		}
	}

	public Snapshot getNewSnapshot() { return newSnapshot; }

	/**
	 * Method to return the Cells that changed.
	 * @return a Set of CellId objects for the Cells that changed.
	 */
	public Set<CellId> changedCells()
	{
		Set<CellId> cellsToUpdate = new HashSet<CellId>();
		for(CellId cid : added.keySet()) cellsToUpdate.add(cid);
		for(CellId cid : removed.keySet()) cellsToUpdate.add(cid);
		return cellsToUpdate;
	}

	/**
	 * Method to return the Cells that changed size.
	 * This is a subset of Cells that changed...it includes only those whose changes affected their bounding box.
	 * @return a Set of CellId objects for the Cells that changed size.
	 */
	public Set<CellId> sizeChangedCells()
	{
		return sizeChanges;
	}

	/**
	 * Method to return a List of objects that were added in a given Cell.
	 * @param cid the CellId of the Cell in question.
	 * @return a List of ImmutableElectricObjects that were created in the Cell.
	 */
	public Set<ImmutableElectricObject> getAdded(CellId cid) { return added.get(cid); }

	/**
	 * Method to return a List of objects that were deleted from a given Cell.
	 * @param cid the CellId of the Cell in question.
	 * @return a Set of ImmutableElectricObjects that were deleted from the Cell.
	 */
	public Set<ImmutableElectricObject> getRemoved(CellId cid) { return removed.get(cid); }

	/**
	 * Method to return a List of Cells that were deleted.
	 * @return a List of CellIds that were deleted in the change.
	 */
	public List<CellId> getDeletedCells() { return deletedCells; }

	/**
	 * Method to return a List of Cells that had export changes.
	 * @return a Set of CellIds that had export changes.
	 */
	public Set<CellId> getChangedExportCells() { return portChanges; }

	/**
	 * Method to return a List of Cells that had variable changes.
	 * @return a Set of CellIds that had variable changes.
	 */
	public Set<CellId> getChangedVariableCells() { return cellVariableChanges; }

	/**
	 * Method to print the changes recorded in this SnapshotAnalyze.
	 */
	public void dumpChanges()
	{
		EDatabase db = EDatabase.currentDatabase();
		System.out.println("++++ SUMMARY OF CHANGES: ++++");
		for(CellId cid : removed.keySet())
		{
			Cell cell = db.getCell(cid);
			Set<ImmutableElectricObject> removedSet = removed.get(cid);
			if (removedSet != null)
			{
				for(ImmutableElectricObject obj : removedSet)
				{
					if (obj instanceof ImmutableNodeInst)
					{
						ImmutableNodeInst ini = (ImmutableNodeInst)obj;
						System.out.println("REMOVED NODE " + describeImmutableObject(cell, ini) + " FROM CELL " + cell.describe(false));
					} else
					{
						ImmutableArcInst iai = (ImmutableArcInst)obj;
						System.out.println("REMOVED ARC " + describeImmutableObject(cell, iai) + " FROM CELL " + cell.describe(false));
					}
				}
			}
			Set<ImmutableElectricObject> addedSet = added.get(cid);
			if (addedSet != null)
			{
				for(ImmutableElectricObject obj : addedSet)
				{
					if (obj instanceof ImmutableNodeInst)
					{
						ImmutableNodeInst ini = (ImmutableNodeInst)obj;
						System.out.println("ADDED NODE " + describeImmutableObject(cell, ini) + " TO CELL " + cell.describe(false));
					} else
					{
						ImmutableArcInst iai = (ImmutableArcInst)obj;
						System.out.println("ADDED ARC " + describeImmutableObject(cell, iai) + " TO CELL " + cell.describe(false));
					}
				}
			}
		}
		for(CellId cid : portChanges)
			System.out.println("EXPORTS CHANGED ON CELL " + cid);
		for(CellId cid : cellVariableChanges)
			System.out.println("VARIABLES CHANGED ON CELL " + cid);
		for(CellId cid : deletedCells)
			System.out.println("DELETED CELL " + cid);

		System.out.println("++++ END OF CHANGE SUMMARY ++++");
	}

	public static String describeImmutableObject(Cell cell, ImmutableElectricObject obj)
	{
		if (obj instanceof ImmutableNodeInst)
		{
			ImmutableNodeInst ini = (ImmutableNodeInst)obj;
			NodeInst ni = cell.getNodeById(ini.nodeId);
			if (ni == null) return "***DELETED NODE***";
			return ni.describe(false);
		}
		ImmutableArcInst iai = (ImmutableArcInst)obj;
		ArcInst ai = cell.getArcById(iai.arcId);
		if (ai == null) return "***DELETED ARC***";
		return ai.describe(false);
	}

	private Set<ImmutableElectricObject> getAddedList(CellId cid)
	{
		Set<ImmutableElectricObject> addedToCell = added.get(cid);
		if (addedToCell == null) added.put(cid, addedToCell = new HashSet<ImmutableElectricObject>());
		return addedToCell;
	}

	private Set<ImmutableElectricObject> getRemovedList(CellId cid)
	{
		Set<ImmutableElectricObject> removedFromCell = removed.get(cid);
		if (removedFromCell == null) removed.put(cid, removedFromCell = new HashSet<ImmutableElectricObject>());
		return removedFromCell;
	}
}
