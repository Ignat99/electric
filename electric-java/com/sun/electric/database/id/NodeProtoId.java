/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NodeProtoId.java
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
package com.sun.electric.database.id;

import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.prototype.NodeProto;

/**
 * The NodeProtoId interface identifies a type of NodeInst .
 * It can be implemented as PrimitiveNode (for primitives from Technologies)
 * or as CellId (for cells in Libraries).
 * <P>
 * The NodeProtoId is immutable and identifies NodeProto independently of threads. It differs from NodeProto objects,
 * some of them (Cells) will be owned by threads  in transactional database. PrimitiveNodes will
 * be shared too, so they are both NodeProtoId and NodeProto.
 */
public interface NodeProtoId {

    /**
     * Returns PortProtoId in this node proto with specified chronological index.
     * @param chronIndex chronological index of ExportId.
     * @return PortProtoId whith specified chronological index.
     * @throws ArrayIndexOutOfBoundsException if no such ExportId.
     */
    public PortProtoId getPortId(int chronIndex);

    /**
     * Returns PortProtoId in this node proto with specified external id.
     * If this external id was requested earlier, the previously created PortProtoId returned,
     * otherwise the new PortProtoId is created.
     * @param externalId external id of PortProtoId.
     * @return PortProtoId with specified external id.
     * @throws NullPointerException if externalId is null.
     */
    public PortProtoId newPortId(String externalId);

    /**
     * Returns true if this NodeProtoId is Id of icon Cell.
     * @return true if this NodeProtoId is Id of icon Cell.
     */
    public boolean isIcon();

    /**
     * Returns true if this NodeProtoId is Id of schematic Cell.
     * @return true if this NodeProtoId is Id of schematic Cell.
     */
    public boolean isSchematic();

    /**
     * Method to return the NodeProto representing NodeProtoId in the specified EDatabase.
     * @param database EDatabase where to get from.
     * @return the NodeProto representing NodeProtoId in the specified database.
     * This method is not properly synchronized.
     */
    public NodeProto inDatabase(EDatabase database);
}
