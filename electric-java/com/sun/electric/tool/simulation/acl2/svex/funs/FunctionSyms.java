/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Functions.java
 *
 * Copyright (c) 2017, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.simulation.acl2.svex.funs;

import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;

/**
 * Our expressions may involve the application of a fixed set of known functions.
 * There are functions available for modeling many bit-vector operations like bitwise and, plus,
 * less-than, and other kinds of hardware operations like resolving multiple drivers, etc.
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____FUNCTIONS>.
 */
public class FunctionSyms
{
    public final static ACL2Object ACL2_ID = fn("ACL2", "ID");
    public final static ACL2Object SV_BITSEL = fn("SV", "BITSEL");
    public final static ACL2Object SV_UNFLOAT = fn("SV", "UNFLOAT");
    public final static ACL2Object SV_BITNOT = fn("SV", "BITNOT");
    public final static ACL2Object SV_BITAND = fn("SV", "BITAND");
    public final static ACL2Object SV_BITOR = fn("SV", "BITOR");
    public final static ACL2Object SV_BITXOR = fn("SV", "BITXOR");
    public final static ACL2Object SV_RES = fn("SV", "RES");
    public final static ACL2Object SV_RESAND = fn("SV", "RESAND");
    public final static ACL2Object SV_RESOR = fn("SV", "RESOR");
    public final static ACL2Object SV_OVERRIDE = fn("SV", "OVERRIDE");
    public final static ACL2Object SV_ONP = fn("SV", "ONP");
    public final static ACL2Object SV_OFFP = fn("SV", "OFFP");
    public final static ACL2Object SV_UAND = fn("SV", "UAND");
    public final static ACL2Object SV_UOR = fn("SV", "UOR");
    public final static ACL2Object SV_UXOR = fn("SV", "UXOR");
    public final static ACL2Object SV_ZEROX = fn("SV", "ZEROX");
    public final static ACL2Object SV_SIGNX = fn("SV", "SIGNX");
    public final static ACL2Object SV_CONCAT = fn("SV", "CONCAT");
    public final static ACL2Object SV_BLKREV = fn("SV", "BLKREV");
    public final static ACL2Object SV_RSH = fn("SV", "RSH");
    public final static ACL2Object SV_LSH = fn("SV", "LSH");
    public final static ACL2Object CL_PLUS = fn("COMMON-LISP", "+");
    public final static ACL2Object SV_BMINUS = fn("SV", "B-");
    public final static ACL2Object SV_UMINUS = fn("SV", "U-");
    public final static ACL2Object CL_STAR = fn("COMMON-LISP", "*");
    public final static ACL2Object CL_SLASH = fn("COMMON-LISP", "/");
    public final static ACL2Object SV_PROCENT = fn("SV", "%");
    public final static ACL2Object SV_XDET = fn("SV", "XDET");
    public final static ACL2Object SV_COUNTONES = fn("SV", "COUNTONES");
    public final static ACL2Object SV_ONEHOT = fn("SV", "ONEHOT");
    public final static ACL2Object SV_ONEHOT0 = fn("SV", "ONEHOT0");
    public final static ACL2Object CL_LT = fn("COMMON-LISP", "<");
    public final static ACL2Object SV_EQ_EQ = fn("SV", "==");
    public final static ACL2Object SV_EQ_EQ_EQ = fn("SV", "===");
    public final static ACL2Object SV_EQ_EQ_QUEST = fn("SV", "==?");
    public final static ACL2Object SV_SAFER_EQ_EQ_QUEST = fn("SV", "SAFER-==?");
    public final static ACL2Object SV_EQ_EQ_QUEST_QUEST = fn("SV", "==??");
    public final static ACL2Object SV_CLOG2 = fn("SV", "CLOG2");
    public final static ACL2Object SV_POW = fn("SV", "POW");
    public final static ACL2Object SV_QUEST = fn("SV", "?");
    public final static ACL2Object SV_QUEST_STAR = fn("SV", "?*");
    public final static ACL2Object SV_BIT_QUEST = fn("SV", "BIT?");
    public final static ACL2Object SV_PARTSEL = fn("SV", "PARTSEL");
    public final static ACL2Object SV_PARTINST = fn("SV", "PARTINST");

    private static ACL2Object fn(String pkgName, String symName)
    {
        ACL2Object sym = ACL2Object.valueOf(pkgName, symName);
        if (!symbol_package_name(sym).stringValueExact().equals(pkgName))
        {
            throw new IllegalArgumentException(sym.rep());
        }
        return sym;
    }
}
