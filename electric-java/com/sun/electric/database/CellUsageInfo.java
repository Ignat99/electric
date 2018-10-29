/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: CellRevision.java
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

import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.Variable;
import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 */
class CellUsageInfo {

    final int instCount;
    final BitSet usedExports;
    final int usedExportsLength;
    final TreeMap<Variable.AttrKey, TextDescriptor.Unit> usedAttributes;

    CellUsageInfo(int instCount, BitSet usedExports, TreeMap<Variable.AttrKey, TextDescriptor.Unit> usedAttributes) {
        this.instCount = instCount;
        usedExportsLength = usedExports.length();
        this.usedExports = usedExportsLength > 0 ? usedExports : CellRevision.EMPTY_BITSET;
        this.usedAttributes = usedAttributes;
    }

    CellUsageInfo with(int instCount, BitSet usedExports, TreeMap<Variable.AttrKey, TextDescriptor.Unit> usedAttributes) {
        usedExports = UsageCollector.bitSetWith(this.usedExports, usedExports);
        usedAttributes = UsageCollector.usedAttributesWith(this.usedAttributes, usedAttributes);
        if (this.instCount == instCount && this.usedExports == usedExports && this.usedAttributes == usedAttributes) {
            return this;
        }
        return new CellUsageInfo(instCount, usedExports, usedAttributes);
    }

    void checkUsage(CellRevision subCellRevision) {
        if (subCellRevision == null) {
            throw new IllegalArgumentException("subCell deleted");
        }
        if (subCellRevision.definedExportsLength < usedExportsLength || subCellRevision.deletedExports.intersects(usedExports)) {
            throw new IllegalArgumentException("exportUsages");
        }
        if (isIcon()) {
            for (Map.Entry<Variable.AttrKey, TextDescriptor.Unit> e : usedAttributes.entrySet()) {
                Variable.AttrKey paramKey = e.getKey();
                Variable param = subCellRevision.d.getParameter(paramKey);
                TextDescriptor.Unit unit = e.getValue();
                if (unit != null) {
                    if (param == null || param.getUnit() != unit) {
                        throw new IllegalArgumentException("param " + paramKey);
                    }
                } else {
                    if (param != null) {
                        throw new IllegalArgumentException("param " + paramKey);
                    }
                }
            }
        }
    }

    private boolean isIcon() {
        return usedAttributes != null;
    }

    void check(CellUsage u) {
        assert instCount > 0;
        assert usedExportsLength == usedExports.length();
        if (usedExportsLength == 0) {
            assert usedExports == CellRevision.EMPTY_BITSET;
        }
        assert isIcon() == u.protoId.isIcon();
        assert !u.parentId.isIcon();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CellUsageInfo) {
            CellUsageInfo that = (CellUsageInfo) o;
            return this.instCount == that.instCount && this.usedExports == that.usedExports && this.usedExportsLength == that.usedExportsLength && (this.usedAttributes == null && that.usedAttributes == null || this.usedAttributes.equals(that.usedAttributes));
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + this.instCount;
        hash = 41 * hash + this.usedExportsLength;
        return hash;
    }
}
