/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Assign.java
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
package com.sun.electric.tool.simulation.acl2.mods;

import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.util.Map;

/**
 * An item of Aliaspair map
 * See<http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____ALIASPAIRS>
 *
 * @param <N> Type of names in Svex variables
 */
public class Aliaspair<N extends SvarName> implements ACL2Backed
{
    public final Lhs<N> lhs;
    public final Lhs<N> rhs;
    private final int hashCode;

    public Aliaspair(Lhs<N> lhs, Lhs<N> rhs)
    {
        this.lhs = lhs;
        this.rhs = rhs;
        hashCode = ACL2Object.hashCodeOfCons(lhs.hashCode(), rhs.hashCode());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof Aliaspair)
        {
            Aliaspair<?> that = (Aliaspair<?>)o;
            return this.hashCode == that.hashCode
                && this.lhs.equals(that.lhs)
                && this.rhs.equals(that.rhs);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return hashCode;
    }

    @Override
    public ACL2Object getACL2Object(Map<ACL2Backed, ACL2Object> backedCache)
    {
        ACL2Object result = backedCache.get(this);
        if (result == null)
        {
            result = hons(lhs.getACL2Object(backedCache), rhs.getACL2Object(backedCache));
            backedCache.put(this, result);
        }
        assert result.hashCode() == hashCode;
        return result;
    }

    @Override
    public String toString()
    {
        return lhs + "==" + rhs;
    }
}
