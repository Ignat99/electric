/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2Backed.java
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
package com.sun.electric.util.acl2;

import java.util.HashMap;
import java.util.Map;

/**
 * Java classes marked by this interface has an ACL2Object tree representation.
 * The hashValue of Java objects implementing this ACL2Backed must be equal to
 * hashValue of its ACL2Object representation.
 */
public interface ACL2Backed
{
    /**
     * Get ACL2Object tree representation of this Java object
     *
     * @return ACL2Object tree representation
     */
    default ACL2Object getACL2Object()
    {
        Map<ACL2Backed, ACL2Object> backedCache = new HashMap<>();
        return getACL2Object(backedCache);
    }

    /**
     * Get ACL2Object tree representation of this Java object
     *
     * @param backedCache a cache of already computed representations
     * @return ACL2Object tree representation
     */
    default ACL2Object getACL2Object(Map<ACL2Backed, ACL2Object> backedCache)
    {
        return getACL2Object();
    }
}
