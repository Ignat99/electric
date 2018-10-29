/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DisplayedText.java
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
package com.sun.electric.database.variable;

import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.user.User;

import java.io.Serializable;

/**
 * DisplayedText is the combination of an ElectricObject and a Variable Key.
 */
public class DisplayedText implements Serializable {

    private ElectricObject eObj;
    private Variable.Key key;

    public DisplayedText(ElectricObject eObj, Variable.Key key) {
        this.eObj = eObj;
        this.key = key;
    }

    public ElectricObject getElectricObject() {
        return eObj;
    }

    public Variable.Key getVariableKey() {
        return key;
    }

    public Variable getVariable() {
        return eObj.getParameterOrVariable(key);
    }

    /**
     * Method to tell whether this DisplayedText stays with its node.
     * The two possibilities are (1) text on invisible pins
     * (2) export names, when the option to move exports with their labels is requested.
     * @return true if this DisplayedText should move with its node.
     */
    public boolean movesWithText(boolean moveNodeWithExport) {
        return objectMovesWithText(eObj, key, moveNodeWithExport);
    }

    /**
     * Method to tell whether text stays with its node.
     * The two possibilities are (1) text on invisible pins
     * (2) export names, when the option to move exports with their labels is requested.
     * @param eObj the ElectricObject with text on it.
     * @param key the Variable Key of the text on the object.
     * @return true if the text should move with its node.
     */
    public static boolean objectMovesWithText(ElectricObject eObj, Variable.Key key, boolean moveNodeWithExport) {
        if (eObj instanceof NodeInst) {
            // moving variable text
            NodeInst ni = (NodeInst) eObj;
            if (ni.isInvisiblePinWithText()) {
                return true;
            }
        } else if (eObj instanceof Export && key == Export.EXPORT_NAME) {
            // moving export text
            Export pp = (Export) eObj;
            if (pp.getOriginalPort().getNodeInst().getProto() == Generic.tech().invisiblePinNode) {
                return true;
            }
            if (moveNodeWithExport/*User.isMoveNodeWithExport()*/) {
                return true;
            }
        }
        return false;
    }
}
