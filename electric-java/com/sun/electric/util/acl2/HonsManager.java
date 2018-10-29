/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: HonsManager.java
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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
class HonsManager
{
    private final String name;
    Map<BigInteger, ACL2Integer> integers = new HashMap<>();
    Map<Rational, ACL2Rational> rationals = new HashMap<>();
    Map<Complex, ACL2Complex> complexes = new HashMap<>();
    Map<String, ACL2String> strings = new HashMap<>();
    Map<ACL2Cons.Key, ACL2Cons> conses = new HashMap<>();

    final ACL2Integer ZERO = ACL2Integer.intern(BigInteger.ZERO, this);
    final ACL2String EMPTY_STR = ACL2String.intern("", this);

    static ThreadLocal<HonsManager> current = new ThreadLocal<>();
    static final HonsManager GLOBAL = new HonsManager("global");

    private HonsManager(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }

    static void init(String name)
    {
        current.set(new HonsManager(name));
    }

    static void close()
    {
        HonsManager hm = current.get();
        System.out.println("Close " + hm.name + " with "
            + hm.integers.size() + " ints "
            + hm.rationals.size() + " rats "
            + hm.complexes.size() + " compls "
            + hm.strings.size() + " strings "
            + hm.conses.size() + " conses");
        hm.conses.clear();
        hm.conses = null;
        hm.integers.clear();
        hm.integers = null;
        hm.rationals.clear();
        hm.rationals = null;
        hm.complexes.clear();
        hm.complexes = null;
        hm.strings.clear();
        hm.strings = null;
        current.set(null);
    }

}
