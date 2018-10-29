/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ArcProtoId.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.database.id;

import com.sun.electric.database.EObjectInputStream;
import com.sun.electric.database.EObjectOutputStream;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.technology.ArcProto;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.Serializable;

/**
 * The ArcProtoId immutable class identifies arc proto independently of threads.
 * It differs from ArcProto objects, which will be owned by threads in transactional database.
 * This class is thread-safe except inCurrentThread method.
 */
public class ArcProtoId implements Serializable {

    /** TechId of this ArcProtoId. */
    public final TechId techId;
    /** ArcProto name */
    public final String name;
    /** ArcProto full name */
    public final String fullName;
    /** Unique index of this ArcProtoId in TechId. */
    public final int chronIndex;

    /**
     * ArcProtoId constructor.
     */
    ArcProtoId(TechId techId, String name, int chronIndex) {
        assert techId != null;
        if (name.length() == 0 || !TechId.jelibSafeName(name)) {
            throw new IllegalArgumentException("ArcProtoId.name");
        }
        this.techId = techId;
        this.name = name;
        fullName = techId.techName + ":" + name;
        this.chronIndex = chronIndex;
    }

    private Object writeReplace() {
        return new ArcProtoIdKey(this);
    }

    private static class ArcProtoIdKey extends EObjectInputStream.Key<ArcProtoId> {

        public ArcProtoIdKey() {
        }

        private ArcProtoIdKey(ArcProtoId arcProtoId) {
            super(arcProtoId);
        }

        @Override
        public void writeExternal(EObjectOutputStream out, ArcProtoId arcProtoId) throws IOException {
            TechId techId = arcProtoId.techId;
            if (techId.idManager != out.getIdManager()) {
                throw new NotSerializableException(arcProtoId + " from other IdManager");
            }
            out.writeInt(techId.techIndex);
            out.writeInt(arcProtoId.chronIndex);
        }

        @Override
        public ArcProtoId readExternal(EObjectInputStream in) throws IOException, ClassNotFoundException {
            int techIndex = in.readInt();
            int chronIndex = in.readInt();
            return in.getIdManager().getTechId(techIndex).getArcProtoId(chronIndex);
        }
    }

    /**
     * Method to return the ArcProto representing ArcProtoId in the specified EDatabase.
     * @param database EDatabase where to get from.
     * @return the ArcProto representing ArcProtoId in the specified database.
     * This method is not properly synchronized.
     */
    public ArcProto inDatabase(EDatabase database) {
        return database.getTechPool().getArcProto(this);
    }

    /**
     * Returns a printable version of this ArcProtoId.
     * @return a printable version of this ArcProtoId.
     */
    @Override
    public String toString() {
        return fullName;
    }

    /**
     * Checks invariants in this ArcProtoId.
     * @exception AssertionError if invariants are not valid
     */
    void check() {
        assert this == techId.getArcProtoId(chronIndex);
        assert name.length() > 0 && TechId.jelibSafeName(name);
    }
}
