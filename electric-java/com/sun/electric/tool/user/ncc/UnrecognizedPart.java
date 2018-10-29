/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: UnrecognizedPart.java
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
package com.sun.electric.tool.user.ncc;

import java.io.Serializable;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.VarContext;

public class UnrecognizedPart implements Serializable {
	static final long serialVersionUID = 0;

    private Cell cell;
    private VarContext context;
    private String name;
    private NodeInst nodeInst;
    
    public UnrecognizedPart(Cell cel, VarContext con, String nm, NodeInst inst) {
        cell = cel;
        context = con;
        name = nm;
        nodeInst = inst;
    }
    
    public Cell       getCell()     { return cell; }
    public VarContext getContext()  { return context; }
    public String     getName()     { return name; }
    public NodeInst   getNodeInst() { return nodeInst; }
}
