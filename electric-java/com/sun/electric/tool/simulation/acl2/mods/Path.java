/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Path.java
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

import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.SvarImpl;
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.List;

/**
 * Type of the names of wires, module instances, and namespaces (such as datatype fields).
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____PATH>.
 */
public abstract class Path implements SvarName
{
    public static ACL2Object KEYWORD_WIRE = ACL2Object.valueOf("KEYWORD", "WIRE");
    public static ACL2Object KEYWORD_SCOPE = ACL2Object.valueOf("KEYWORD", "SCOPE");

    public static Path fromACL2(ACL2Object impl)
    {
        Path result;
        if (consp(impl).bool() && !Util.KEYWORD_ANONYMOIUS.equals(car(impl).bool()))
        {
            Name namespace = Name.fromACL2(car(impl));
            Path scope = fromACL2(cdr(impl));
            result = new Scope(namespace, scope);
        } else
        {
            result = new Wire(Name.fromACL2(impl));
        }
        assert result.hashCode() == impl.hashCode();
        return result;
    }

    public static Path simplePath(Name name)
    {
        return new Wire(name);
    }

    public static Path makePath(List<Name> scopes, Name name)
    {
        Path path = simplePath(name);
        for (int i = scopes.size() - 1; i >= 0; i--)
        {
            path = new Scope(scopes.get(i), path);
        }
        return path;
    }

    public abstract ACL2Object getKind();

    public int getDepth()
    {
        Path path = this;
        int depth = 0;
        while (path instanceof Scope)
        {
            path = ((Scope)path).subpath;
            depth++;
        }
        return depth;
    }

    public abstract Path append(Path y);

    @Override
    public String toString(BigInteger mask)
    {
        String s = toString();
        if (mask != null)
        {
            s += "#" + mask.toString(16);
        }
        return s;
    }

    public static class Wire extends Path
    {
        public final Name name;

        public Wire(Name name)
        {
            if (name == null)
            {
                throw new NullPointerException();
            }
            this.name = name;
        }

        @Override
        public ACL2Object getKind()
        {
            return KEYWORD_WIRE;
        }

        @Override
        public boolean isSimpleSvarName()
        {
            return name.isSimpleSvarName();
        }

        @Override
        public Path append(Path y)
        {
            return new Path.Scope(name, y);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            return o instanceof Wire && name.equals(((Wire)o).name);
        }

        @Override
        public int hashCode()
        {
            return name.hashCode();
        }

        @Override
        public ACL2Object getACL2Object()
        {
            ACL2Object result = name.getACL2Object();
            assert result.hashCode() == hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return name.toString();
        }
    }

    public static class Scope extends Path
    {
        public final Path subpath;
        public final Name namespace;
        private final int hashCode;

        Scope(Name namespace, Path subpath)
        {
            this.namespace = namespace;
            this.subpath = subpath;
            hashCode = ACL2Object.hashCodeOfCons(namespace.hashCode(), subpath.hashCode());
        }

        @Override
        public ACL2Object getKind()
        {
            return KEYWORD_SCOPE;
        }

        @Override
        public boolean isSimpleSvarName()
        {
            return false;
        }

        @Override
        public Path append(Path y)
        {
            return new Path.Scope(namespace, subpath.append(y));
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o instanceof Scope)
            {
                Scope that = (Scope)o;
                return this.hashCode == that.hashCode
                    && this.namespace.equals(that.namespace)
                    && this.subpath.equals(that.subpath);
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            return hashCode;
        }

        @Override
        public ACL2Object getACL2Object()
        {
            ACL2Object result = hons(namespace.getACL2Object(), subpath.getACL2Object());
            assert result.hashCode() == hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return namespace + "." + subpath;
        }
    }
}
