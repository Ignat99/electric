/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SpiceModel.java
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
package com.sun.electric.tool.io.input.spicenetlist;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class SpiceModel
{
    private final String modPrefix;
    private final String flag;
    /**
     * Map from model suffix (like ".1) to a map from parameter names to parameter values.
     */
    private final Map<String, Map<String, String>> paramSets = new LinkedHashMap<>();

    SpiceModel(String modPrefix, String flag)
    {
        this.modPrefix = modPrefix;
        this.flag = flag;
    }

    public String getModPrefix()
    {
        return modPrefix;
    }

    public String getFlag()
    {
        return flag;
    }

    public Map<String, String> newParams(String modSuffix)
    {
        modSuffix = modSuffix.toLowerCase();
        Map<String, String> newParams = new LinkedHashMap<>();
        paramSets.put(modSuffix, newParams);
        return newParams;
    }

    public void write(PrintStream out, Set<SpiceModel> usedModels)
    {
        if (usedModels != null && !usedModels.contains(this))
        {
            return;
        }
        for (Map.Entry<String, Map<String, String>> e : paramSets.entrySet())
        {
            String modSuffix = e.getKey();
            Map<String, String> paramSet = e.getValue();
            StringBuilder buf = new StringBuilder();
            buf.append(".model ").append(modPrefix).append(modSuffix)
                .append(" ").append(flag).append(" ");
            for (Map.Entry<String, String> e1 : paramSet.entrySet())
            {
                String key = e1.getKey();
                String value = e1.getValue();
                buf.append(key);
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
}
