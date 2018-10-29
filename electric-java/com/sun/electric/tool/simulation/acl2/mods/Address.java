/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Address.java
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
import com.sun.electric.tool.simulation.acl2.svex.SvarName;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.List;

/**
 * Type of the names of wires, module instances, and namespaces (such as datatype fields).
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____ADDRESS>.
 */
public class Address implements SvarName
{
    public static final ACL2Object KEYWORD_ADDRESS = ACL2Object.valueOf("KEYWORD", "ADDRESS");
    public static final ACL2Object KEYWORD_ROOT = ACL2Object.valueOf("KEYWORD", "ROOT");
    public static final int INDEX_NIL = -1;
    public static final int SCOPE_ROOT = -1;

    public final Path path;
    public final int index;
    public final int scope;
    private final int hashCode;

    Address(Path path)
    {
        this.path = path;
        index = INDEX_NIL;
        scope = 0;
        hashCode = path.hashCode();
    }

    Address(Path path, int index, int scope)
    {
        if (path == null)
        {
            throw new NullPointerException();
        }
        if (index < INDEX_NIL || scope < SCOPE_ROOT)
        {
            throw new IllegalArgumentException();
        }
        this.path = path;
        this.index = index;
        this.scope = scope;
        if (index == INDEX_NIL && scope == 0)
        {
            hashCode = path.hashCode();
        } else
        {
            hashCode = ACL2Object.hashCodeOfCons(
                KEYWORD_ADDRESS.hashCode(),
                ACL2Object.hashCodeOfCons(
                    path.hashCode(),
                    ACL2Object.hashCodeOfCons(
                        index >= 0 ? ACL2Object.hashCodeOf(index) : ACL2Object.HASH_CODE_NIL,
                        ACL2Object.hashCodeOfCons(
                            scope >= 0 ? ACL2Object.hashCodeOf(scope) : KEYWORD_ROOT.hashCode(),
                            ACL2Object.HASH_CODE_NIL))));
        }
    }

    public static Address valueOf(Path path)
    {
        return new Address(path);
    }

    @Override
    public boolean isSimpleSvarName()
    {
        return path.isSimpleSvarName() && index == INDEX_NIL && scope == 0;
    }

    public static Address fromACL2(ACL2Object impl)
    {
        Address result;
        if (consp(impl).bool() && KEYWORD_ADDRESS.equals(car(impl)))
        {
            List<ACL2Object> list = Util.getList(impl, true);
            Path path = Path.fromACL2(list.get(1));
            int index;
            if (integerp(list.get(2)).bool())
            {
                index = list.get(2).intValueExact();
                Util.check(index >= 0);
            } else
            {
                Util.checkNil(list.get(2));
                index = INDEX_NIL;
            }
            int scope;
            if (KEYWORD_ROOT.equals(list.get(3)))
            {
                scope = SCOPE_ROOT;
            } else
            {
                scope = list.get(3).intValueExact();
                Util.check(scope >= 0);
            }
            result = new Address(path, index, scope);
        } else
        {
            Path path = Path.fromACL2(impl);
            result = new Address(path);
        }
        assert result.hashCode() == impl.hashCode();
        return result;
    }

    public Path getPath()
    {
        return path;
    }

    public Integer getIndex()
    {
        return index >= 0 ? index : null;
    }

    public Integer getScope()
    {
        return scope >= 0 ? scope : null;
    }

    @Override
    public String toString(BigInteger mask)
    {
        return path.toString(mask);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof Address)
        {
            Address that = (Address)o;
            return this.hashCode == that.hashCode
                && this.path.equals(that.path)
                && this.index == that.index
                && this.scope == that.scope;
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
        ACL2Object result;
        ACL2Object pathImpl = path.getACL2Object();
        if (index == INDEX_NIL && scope == 0)
        {
            result = pathImpl;
        } else
        {
            ACL2Object indexImpl = index >= 0 ? ACL2Object.valueOf(index) : NIL;
            ACL2Object scopeImpl = scope >= 0 ? ACL2Object.valueOf(scope) : KEYWORD_ROOT;
            result = hons(
                KEYWORD_ADDRESS,
                hons(pathImpl,
                    hons(indexImpl,
                        hons(scopeImpl, NIL))));
        }
        assert result.hashCode() == hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        String s = path.toString();
        if (scope == SCOPE_ROOT)
        {
            s = "/" + s;
        } else if (scope > 0)
        {
            for (int i = 0; i < scope; i++)
            {
                s = "../" + s;
            }
        }
        return s;
    }

    public static boolean svarIdxaddrOkp(Svar<Address> svar, int bound)
    {
        Address addr = svar.getName();
        return addr.index == INDEX_NIL || addr.index < bound;
    }

    public static class SvarNameBuilder implements SvarName.Builder<Address>
    {
        @Override
        public Address fromACL2(ACL2Object impl)
        {
            return Address.fromACL2(impl);
        }
    }
}
