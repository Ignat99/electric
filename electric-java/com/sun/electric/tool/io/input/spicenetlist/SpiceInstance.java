/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SpiceNetlistReader.java
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
package com.sun.electric.tool.io.input.spicenetlist;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

/**
 * User: gainsley
 * Date: Aug 3, 2006
 */
public class SpiceInstance
{
    private final char type;                  // spice type
    private final String name;
    private String modName;
    private SpiceModel model;
    private final List<String> nets = new ArrayList<>();
    private final SpiceSubckt subckt;              // may be null if primitive element
    private final Map<String, String> params = new LinkedHashMap<>();

    public SpiceInstance(String typeAndName)
    {
        this.type = typeAndName.charAt(0);
        this.name = typeAndName.substring(1);
        this.subckt = null;
    }

    public SpiceInstance(SpiceSubckt subckt, String name)
    {
        this.type = 'x';
        this.name = name;
        this.subckt = subckt;
//        for (String key : subckt.getParams().keySet()) {
//            // set default param values
//            this.params.put(key, subckt.getParams().get(key));
//        }
    }

    public char getType()
    {
        return type;
    }

    public String getName()
    {
        return name;
    }

    public List<String> getNets()
    {
        return nets;
    }

    public void addModName(String modName)
    {
        this.modName = modName;
    }

    void linkModel(SpiceNetlistReader reader, List<SpiceSubckt> currentSubcktStack)
    {
        if (modName == null)
            return;
        for (int j = currentSubcktStack.size() - 1; model == null && j >= 0; j--)
        {
            model = currentSubcktStack.get(j).findModel(modName);
        }
        if (model == null)
        {
            model = reader.getGlobalModels().get(modName.toLowerCase());
        }
        if (model == null)
        {
            reader.prErr("Cannot find model for " + modName);
        }
    }

    public void addNet(String net)
    {
        nets.add(net);
    }

    public Map<String, String> getParams()
    {
        return params;
    }

    public SpiceSubckt getSubckt()
    {
        return subckt;
    }

    void markUsed(Set<SpiceSubckt> usedSubckts, Set<SpiceModel> usedModels)
    {
        if (subckt != null) {
            subckt.markUsed(usedSubckts, usedModels);
        }
        if (model != null) {
            usedModels.add(model);
        }
    }

    public void write(PrintStream out)
    {
        StringBuilder buf = new StringBuilder();
        buf.append(type);
        buf.append(name);
        buf.append(" ");
        for (String net : nets)
        {
            buf.append(net);
            buf.append(" ");
        }
        if (model != null)
        {
            buf.append(model.getModPrefix()).append(" ");
        }
        if (subckt != null)
        {
            buf.append(subckt.getName());
            buf.append(" ");
        }
        for (String key : params.keySet())
        {
            buf.append(key);
            String value = params.get(key);
            if (value != null)
            {
                buf.append("=").append(value);
            }
            buf.append(" ");
        }
        buf.append("\n");
        SpiceNetlistReader.multiLinePrint(out, false, buf.toString());
    }
}
