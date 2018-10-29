/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2Character.java
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

/**
 * ACL2 character.
 * This is an atom. Its value is 8-bit ASCII character.
 */
class ACL2Character extends ACL2Object
{
    final char c;
    private static final ACL2Character[] allNormed = new ACL2Character[256];

    static
    {
        for (char c = 0; c < allNormed.length; c++)
        {
            allNormed[c] = new ACL2Character(c);
        }
    }

    private ACL2Character(char c)
    {
        super(hashCodeOf(c), HonsManager.GLOBAL);
        this.c = c;
    }

    static ACL2Character intern(char c)
    {
        return allNormed[c];
    }

    @Override
    public String rep()
    {
        return "#\\" + c;
    }
}
