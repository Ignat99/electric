/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LibraryBackup.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.database;

import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.id.IdWriter;
import com.sun.electric.database.id.LibId;
import com.sun.electric.util.collections.ImmutableArrayList;

import java.io.IOException;

/**
 *
 */
public class LibraryBackup {

    public static final LibraryBackup[] NULL_ARRAY = {};
    public static final ImmutableArrayList<LibraryBackup> EMPTY_LIST = ImmutableArrayList.of();
    /**
     * Library persistent data.
     */
    public final ImmutableLibrary d;
    /**
     * True if library needs saving to disk.
     */
    public final boolean modified;
    /**
     * Array of referenced libs
     */
    public final LibId[] referencedLibs;

    /**
     * Creates a new instance of LibraryBackup
     */
    public LibraryBackup(ImmutableLibrary d, boolean modified, LibId[] referencedLibs) {
        this.d = d;
        this.modified = modified;
        this.referencedLibs = referencedLibs;
    }

    /**
     * Returns LibraryBackup which differs from this LibraryBackup by "modified" flag set.
     * @return LibraryBackup with "modified" flag set.
     */
    LibraryBackup withModified() {
        if (modified) {
            return this;
        }
        return new LibraryBackup(d, true, referencedLibs);
    }

    /**
     * Returns LibraryBackup which differs from this LibraryBackup by renamed Ids.
     * @param idMapper a map from old Ids to new Ids.
     * @return LibraryBackup with renamed Ids.
     */
    LibraryBackup withRenamedIds(IdMapper idMapper) {
        ImmutableLibrary d = this.d.withRenamedIds(idMapper);
        LibId[] referencedLibs = null;
        for (int i = 0; i < this.referencedLibs.length; i++) {
            LibId oldLibId = this.referencedLibs[i];
            LibId newLibId = idMapper.get(oldLibId);
            if (newLibId != oldLibId && referencedLibs == null) {
                referencedLibs = new LibId[this.referencedLibs.length];
                System.arraycopy(this.referencedLibs, 0, referencedLibs, 0, referencedLibs.length);
            }
            if (referencedLibs != null) {
                referencedLibs[i] = newLibId;
            }
        }
        if (referencedLibs == null) {
            referencedLibs = this.referencedLibs;
        }
        if (this.d == d && this.referencedLibs == referencedLibs) {
            return this;
        }
        LibraryBackup newBackup = new LibraryBackup(d, true, referencedLibs);
        newBackup.check();
        return newBackup;
    }

    /**
     * Writes this LibraryBackup to IdWriter.
     * @param writer where to write.
     */
    void write(IdWriter writer) throws IOException {
        d.write(writer);
        writer.writeBoolean(modified);
        writer.writeInt(referencedLibs.length);
        for (int i = 0; i < referencedLibs.length; i++) {
            writer.writeLibId(referencedLibs[i]);
        }
    }

    /**
     * Reads LibraryBackup from SnapshotReader.
     * @param reader where to read.
     */
    static LibraryBackup read(IdReader reader) throws IOException {
        ImmutableLibrary d = ImmutableLibrary.read(reader);
        boolean modified = reader.readBoolean();
        int refsLength = reader.readInt();
        LibId[] refs = new LibId[refsLength];
        for (int i = 0; i < refsLength; i++) {
            refs[i] = reader.readLibId();
        }
        return new LibraryBackup(d, modified, refs);
    }

    /**
     * Checks invariant of this CellBackup.
     * @throws AssertionError if invariant is broken.
     */
    public void check() {
        d.check();
        for (LibId libId : referencedLibs) {
            assert libId != null;
        }
    }

    @Override
    public String toString() {
        return d.toString();
    }
}
