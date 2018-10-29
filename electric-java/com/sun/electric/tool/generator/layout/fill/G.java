/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: G.java
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
package com.sun.electric.tool.generator.layout.fill;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.tool.generator.layout.LayoutLib;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.ArcProto;

/**
 * Fill Cell Globals
 * User: gg151869
 * Date: Sep 19, 2006
 */
public class G {
    public static double DEF_SIZE = LayoutLib.DEF_SIZE;
    public static ArcInst noExtendArc(ArcProto pa, EditingPreferences ep, double w,
                                      PortInst p1, PortInst p2) {
        ArcInst ai = LayoutLib.newArcInst(pa, ep, w, p1, p2);
        ai.setHeadExtended(false);
        ai.setTailExtended(false);
        return ai;
    }
    public static ArcInst newArc(ArcProto pa, EditingPreferences ep, double w,
                                 PortInst p1, PortInst p2) {
        return LayoutLib.newArcInst(pa, ep, w, p1, p2);
    }
    private G(){}
}
