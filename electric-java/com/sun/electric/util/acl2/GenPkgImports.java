/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2Symbol.java
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

import static com.sun.electric.util.acl2.ACL2.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generate pkg-imports resource file
 * from result of the dump-pkg-import.lisp script.
 */
public class GenPkgImports
{
    private final Map<String, Map<String, String>> packages = new TreeMap<>();
    private final Map<String, Set<String>> dependences = new TreeMap<>();
    private final Map<String, Map<String, String>> sortedPackages = new LinkedHashMap<>();

    private static void check(boolean b)
    {
        assert b;
    }

    private static void checkNotNil(ACL2Object x)
    {
        check(!NIL.equals(x));
    }

    private int calcDiff(Map<String, String> table, Map<String, String> real)
    {
        int intersectionSize = 0;
        for (Map.Entry<String, String> e : real.entrySet())
        {
            String symName = e.getKey();
            String pkgName = e.getValue();
            if (pkgName.equals(table.get(symName)))
            {
                intersectionSize++;
            }
        }
        return table.size() + real.size() - 2 * intersectionSize;
    }

    private void readPackages(ACL2Object alist)
    {
        while (consp(alist).bool())
        {
            ACL2Object pair = car(alist);
            checkNotNil(consp(pair));
            checkNotNil(stringp(car(pair)));
            String pk = car(pair).stringValueExact();
            Map<String, String> imports = new LinkedHashMap<>();
            ACL2Object impAlist = cdr(pair);
            while (consp(impAlist).bool())
            {
                ACL2Object imp = car(impAlist);
                checkNotNil(consp(imp));
                String pkgName = car(imp).stringValueExact();
                String symName = cdr(imp).stringValueExact();
                String old = imports.put(symName, pkgName);
                check(old == null);
                impAlist = cdr(impAlist);
            }
            packages.put(pk, imports);
            alist = cdr(alist);
        }
    }

    private void initDependencies()
    {
        for (Map.Entry<String, Map<String, String>> e : packages.entrySet())
        {
            String pk = e.getKey();
            Map<String, String> imports = e.getValue();
            Set<String> impPackages = new TreeSet<>();
            for (String impPkg : imports.values())
            {
                impPackages.add(impPkg);
            }
            dependences.put(pk, impPackages);
        }
        for (String pk : dependences.keySet())
        {
            visitDeps(sortedPackages, pk);
        }
        check(sortedPackages.size() == dependences.size());
    }

    private void visitDeps(Map<String, Map<String, String>> visited, String top)
    {
        if (!visited.containsKey(top))
        {
            Set<String> deps = dependences.get(top);
            check(deps != null);
            for (String dep : deps)
            {
                visitDeps(visited, dep);
            }
            Map<String, String> old = visited.put(top, packages.get(top));
            check(old == null);
        }
    }

    private String q(String s)
    {
        if (s.isEmpty() || s.indexOf(' ') >= 0)
        {
            s = '|' + s + '|';
        }
        return s;
    }

    private GenPkgImports(ACL2Object root)
    {
        readPackages(root);
        initDependencies();

        for (Map.Entry<String, Map<String, String>> e : sortedPackages.entrySet())
        {
            String pk = e.getKey();
            Map<String, String> imports = e.getValue();

            int bestDiff = imports.size();
            String bestPk = null;
            Map<String, String> bestImports = Collections.emptyMap();
            for (Map.Entry<String, Map<String, String>> e1 : sortedPackages.entrySet())
            {
                String pk1 = e1.getKey();
                Map<String, String> imports1 = e1.getValue();
                if (pk1.equals(pk))
                {
                    break;
                }
                int diff = calcDiff(imports1, imports);
                if (diff < bestDiff)
                {
                    bestDiff = diff;
                    bestPk = pk1;
                    bestImports = imports1;
                }
            }

            System.out.print(q(pk));
            if (bestPk != null)
            {
                System.out.print(" " + q(bestPk));
            }
            System.out.println();
            for (Map.Entry<String, String> e1 : bestImports.entrySet())
            {
                String symName = e1.getKey();
                String pkgName = e1.getValue();
                if (!pkgName.equals(imports.get(symName)))
                {
                    System.out.println("-" + q(pkgName) + ":" + q(symName));
                }
            }
            for (Map.Entry<String, String> e1 : imports.entrySet())
            {
                String symName = e1.getKey();
                String pkgName = e1.getValue();
                if (!pkgName.equals(bestImports.get(symName)))
                {
                    System.out.println("+" + q(pkgName) + ":" + q(symName));
                }
            }
            System.out.println();
        }
    }

    public static void gen(File f)
    {
        try
        {
            ACL2Object.initHonsMananger("PkgImports");
            ACL2Reader sr = new ACL2Reader(f);
            new GenPkgImports(sr.root);
        } catch (IOException e)
        {
        } finally
        {
            ACL2Object.closeHonsManager();
        }
    }
}
