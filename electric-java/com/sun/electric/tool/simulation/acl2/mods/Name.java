/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Name.java
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

import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Backed;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;

/**
 * Type of the names of wires, module instances, and namespaces (such as datatype fields).
 * See <http://www.cs.utexas.edu/users/moore/acl2/manuals/current/manual/?topic=SV____NAME>.
 */
public abstract class Name implements ACL2Backed
{

    public static final Name SELF = new Name()
    {
        @Override
        public boolean isSimpleSvarName()
        {
            return true;
        }

        @Override
        public int hashCode() // boolean equals(Object o) is default
        {
            return Util.KEYWORD_SELF.hashCode();
        }

        @Override
        public ACL2Object getACL2Object()
        {
            return Util.KEYWORD_SELF;
        }

        @Override
        public String toString()
        {
            return ":SELF";
        }
    };

    public static Name fromACL2(ACL2Object impl)
    {
        Name result;
        if (stringp(impl).bool())
        {
            result = valueOf(impl.stringValueExact());
        } else if (integerp(impl).bool())
        {
            result = valueOf(impl.intValueExact());
        } else if (impl.equals(Util.KEYWORD_SELF))
        {
            result = SELF;
        } else
        {
            result = new AnonymousImpl(impl);
        }
        assert result.hashCode() == impl.hashCode();
        return result;
    }

    public static Name valueOf(String s)
    {
        return new StringImpl(s);
    }

    public static Name valueOf(BigInteger i)
    {
        return new IntegerImpl(i);
//        return i.signum() >= 0 && i.bitLength() < Integer.SIZE
//            ? new IndexName(i.intValueExact())
//            : new IntegerImpl(i);
    }

    public static Name valueOf(int i)
    {
        return new IntegerImpl(BigInteger.valueOf(i));
//        return i >= 0 ? new IndexName(i) : new IntegerImpl(BigInteger.valueOf(i));
    }

    public boolean isString()
    {
        return false;
    }

    public boolean isInteger()
    {
        return false;
    }

    public boolean isSimpleSvarName()
    {
        return false;
    }

    public String toLispString()
    {
        return toString();
    }

    private static class StringImpl extends Name
    {
        private String s;

        StringImpl(String s)
        {
            this.s = s;
        }

        @Override
        public boolean isString()
        {
            return true;
        }

        @Override
        public boolean isSimpleSvarName()
        {
            return true;
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof StringImpl && s.equals(((StringImpl)o).s);
        }

        @Override
        public int hashCode()
        {
            return ACL2Object.hashCodeOf(s);
        }

        @Override
        public ACL2Object getACL2Object()
        {
            ACL2Object result = honscopy(ACL2Object.valueOf(s));
            assert result.hashCode() == hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return s;
        }

        @Override
        public String toLispString()
        {
            return "\"" + s + "\"";
        }
    }

    private static class IntegerImpl extends Name
    {
        private BigInteger val;

        IntegerImpl(BigInteger val)
        {
//            if (val.signum() >= 0  val.bitLength() <= Integer.SIZE - 1)
//            {
//                throw new IllegalArgumentException();
//            }
            this.val = val;
        }

        @Override
        public boolean isInteger()
        {
            return true;
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof IntegerImpl && val.equals(((IntegerImpl)o).val);
        }

        @Override
        public int hashCode()
        {
            return ACL2Object.hashCodeOf(val);
        }

        @Override
        public ACL2Object getACL2Object()
        {
            ACL2Object result = honscopy(ACL2Object.valueOf(val));
            assert result.hashCode() == hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return "'" + val;
        }
    }

    private static class AnonymousImpl extends Name
    {
        private ACL2Object args;

        AnonymousImpl(ACL2Object impl)
        {
            if (!car(impl).equals(Util.KEYWORD_ANONYMOIUS))
            {
                throw new IllegalArgumentException();
            }
            this.args = cdr(impl);
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof AnonymousImpl && args.equals(((AnonymousImpl)o).args);
        }

        @Override
        public int hashCode()
        {
            return ACL2Object.hashCodeOfCons(Util.KEYWORD_ANONYMOIUS.hashCode(), args.hashCode());
        }

        @Override
        public ACL2Object getACL2Object()
        {
            ACL2Object result = hons(Util.KEYWORD_ANONYMOIUS, args);
            assert result.hashCode() == hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            return "'(:ANONYMOUS . " + args.rep() + ")";
        }
    }
}
