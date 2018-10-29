/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: IdMapper.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.database;

import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.id.LibId;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;

/**
 * Class to describe mapping of Electric database Ids (LibIds, CellIds, ExportIds).
 */
public class IdMapper implements Serializable {

    private final HashMap<LibId, LibId> libIdMap = new HashMap<LibId, LibId>();
    private final HashMap<CellId, CellId> cellIdMap = new HashMap<CellId, CellId>();
    private final HashMap<ExportId, ExportId> exportIdMap = new HashMap<ExportId, ExportId>();

    /** Creates a new instance of IdMapper */
    public IdMapper() {
    }

    public static IdMapper renameLibrary(Snapshot snapshot, LibId oldLibId, LibId newLibId) {
        IdMapper idMapper = new IdMapper();
        idMapper.libIdMap.put(oldLibId, newLibId);
        for (CellBackup cellBackup : snapshot.cellBackups) {
            if (cellBackup == null) {
                continue;
            }
            CellId oldCellId = cellBackup.cellRevision.d.cellId;
            if (oldCellId.libId != oldLibId) {
                continue;
            }
            CellId newCellId = newLibId.newCellId(oldCellId.cellName);
            idMapper.moveCell(cellBackup, newCellId);
        }
        return idMapper;
    }

    public static IdMapper renameCell(Snapshot snapshot, CellId oldCellId, CellId newCellId) {
        IdMapper idMapper = new IdMapper();
        CellBackup cellBackup = snapshot.getCell(oldCellId);
        idMapper.moveCell(cellBackup, newCellId);
        return idMapper;
    }

    public static IdMapper consolidateExportIds(Snapshot snapshot) {
        IdMapper idMapper = new IdMapper();
        for (CellBackup cellBackup : snapshot.cellBackups) {
            if (cellBackup == null) {
                continue;
            }
            CellRevision cellRevision = cellBackup.cellRevision;
            CellId cellId = cellRevision.d.cellId;
            for (ImmutableExport e : cellRevision.exports) {
                if (e.name.toString().equals(e.exportId.externalId)) {
                    continue;
                }
                idMapper.exportIdMap.put(e.exportId, cellId.newPortId(e.name.toString()));
            }
        }
        return idMapper;
    }

    /**
     * Add to this idMapper mapping from old cellBackup to new cellId together with all exports.
     * @param cellBackup old cellBackup
     * @param newCellId new CellId.
     */
    public void moveCell(CellBackup cellBackup, CellId newCellId) {
        CellId oldCellId = cellBackup.cellRevision.d.cellId;
        if (oldCellId.cellName.getView() != newCellId.cellName.getView()) {
            if (oldCellId.isIcon() || oldCellId.isSchematic() || newCellId.isIcon() || newCellId.isSchematic()) {
                throw new IllegalArgumentException("Can't rename " + oldCellId + " to " + newCellId);
            }
        }
        cellIdMap.put(oldCellId, newCellId);
    }

    /**
     * Get mappinmg of LibId.
     * @param key key LibId.
     * @return LibId which is the mapping of the key.
     */
    public LibId get(LibId key) {
        LibId value = libIdMap.get(key);
        return value != null ? value : key;
    }

    /**
     * Get mappinmg of CellId.
     * @param key key CellId.
     * @return CellId which is the mapping of the key.
     */
    public CellId get(CellId key) {
        CellId value = cellIdMap.get(key);
        return value != null ? value : key;
    }

    /**
     * Get mapping of ExportId.
     * @param key key ExportId.
     * @return ExportId which is the mapping of the key.
     */
    public ExportId get(ExportId key) {
        ExportId newExportId = exportIdMap.get(key);
        if (newExportId != null) {
            return newExportId;
        }
        CellId newParentId = cellIdMap.get(key.parentId);
        return newParentId != null ? newParentId.newPortId(key.externalId) : key;
    }

    public Collection<CellId> getNewCellIds() {
        return cellIdMap.values();
    }
}
