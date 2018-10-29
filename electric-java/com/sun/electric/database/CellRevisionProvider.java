/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellRevisionProvider.java
 * Written by: Dmitry Nadezhin.
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

/**
 * Subclasses of this abstract class provides variants of
 * ImmutableNodeInst/ImmuyableArcInst/ImmutableExport lists that are used in
 * CellRevision.
 */
public abstract class CellRevisionProvider {

    static final CellRevisionProvider INSTANCE = loadImplementation();

    private static CellRevisionProvider loadImplementation() {
        switch (Integer.getInteger("com.sun.electric.database.CellRevision", 0)) {
            case 1:
                return new CellRevisionProviderDefault();
            case 2:
                return new CellRevisionConn0.CellRevisionProvider();
            default:
                return new CellRevisionConn.CellRevisionProvider();
        }
    }

    /**
     * Creates a new instance of CellRevision
     */
    public abstract CellRevision createCellRevision(ImmutableCell c);

    /**
     * Creates a list of ImmutableNodeInsts that contains specified elements.
     * ImmutableNodeInsts must be ordered by name according to
     * TextUtils.STRING_NUMBER_ORDER. New list can share some fragments with an
     * old list.
     *
     * @param elems array with specified elements in correct order.
     * @param oldList old list
     * @return a list of ImmutableNodeInsts
     * @throws IllegalArgumentException if elements are not properly ordered
     */
    protected abstract ImmutableNodeInst.Iterable createNodeList(ImmutableNodeInst[] elems, ImmutableNodeInst.Iterable oldList);

    /**
     * Creates a list of ImmutableArcInsts that contains specified elements.
     * ImmutableArcInsts must be ordered by name according to
     * TextUtils.STRING_NUMBER_ORDER and the by arcId. New list can share some
     * fragments with an old list.
     *
     * @param elems array with specified elements in correct order.
     * @param oldList old list
     * @return a list of ImmutableArcInsts
     * @throws IllegalArgumentException if elements are not properly ordered
     */
    protected abstract ImmutableArcInst.Iterable createArcList(ImmutableArcInst[] elems, ImmutableArcInst.Iterable oldList);

    /**
     * Creates a list of ImmutableExports that contains specified elements.
     * ImmutableExports must be ordered by name according to
     * TextUtils.STRING_NUMBER_ORDER. New list can share some fragments with an
     * old list.
     *
     * @param elems array with specified elements in correct order.
     * @param oldList old list
     * @return a list of ImmutableExports
     * @throws IllegalArgumentException if elements are not properly ordered
     */
    protected abstract ImmutableExport.Iterable createExportList(ImmutableExport[] elems, ImmutableExport.Iterable oldList);
}
