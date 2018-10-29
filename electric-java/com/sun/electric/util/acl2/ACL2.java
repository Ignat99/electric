/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ACL2.java
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

/**
 * A collection of static methods correspondent to lisp functions available in ACL2 lisp package.
 */
public class ACL2
{
    public static final ACL2Object NIL = ACL2Symbol.NIL;
    public static final ACL2Object T = ACL2Symbol.T;
    public static final ACL2Object IF = ACL2Symbol.COMMON_LISP.getSymbol("IF");
    public static final ACL2Object LAMBDA = ACL2Symbol.COMMON_LISP.getSymbol("LAMBDA");
    public static final ACL2Object LIST = ACL2Symbol.COMMON_LISP.getSymbol("LIST");
    public static final ACL2Object QUOTE = ACL2Symbol.COMMON_LISP.getSymbol("QUOTE");

    public static ACL2Object acl2_numberp(ACL2Object x)
    {
        return ACL2Object.valueOf(x.isACL2Number());
    }

    public static ACL2Object binaryStar(ACL2Object x, ACL2Object y)
    {
        return x.binaryStar(y);
    }

    public static ACL2Object binaryPlus(ACL2Object x, ACL2Object y)
    {
        return x.binaryPlus(y);
    }

    public static ACL2Object unaryMinus(ACL2Object x)
    {
        return x.unaryMinus();
    }

    public static ACL2Object unarySlash(ACL2Object x)
    {
        return x.unarySlash();
    }

    public static ACL2Object lt(ACL2Object x, ACL2Object y)
    {
        return ACL2Symbol.valueOf(x.compareTo(y) < 0);
    }

    public static ACL2Object car(ACL2Object x)
    {
        if (x instanceof ACL2Cons)
        {
            return ((ACL2Cons)x).car;
        }
        return ACL2Symbol.NIL;
    }

    public static ACL2Object cdr(ACL2Object x)
    {
        if (x instanceof ACL2Cons)
        {
            return ((ACL2Cons)x).cdr;
        }
        return ACL2Symbol.NIL;
    }

    public static ACL2Object code_char(ACL2Object x)
    {
        if (x instanceof ACL2Integer)
        {
            BigInteger v = ((ACL2Integer)x).v;
            if (v.signum() >= 0 && v.bitLength() <= 8)
            {
                return ACL2Character.intern((char)v.shortValueExact());
            }
        }
        return ACL2Character.intern((char)0);
    }

    public static ACL2Object characterp(ACL2Object x)
    {
        return ACL2Symbol.valueOf(x instanceof ACL2Character);
    }

    public static ACL2Object char_code(ACL2Object x)
    {
        if (x instanceof ACL2Character)
        {
            return new ACL2Integer(BigInteger.valueOf(((ACL2Character)x).c));
        }
        return ACL2Object.zero();
    }

    public static ACL2Object complex(ACL2Object x, ACL2Object y)
    {
        return ACL2Object.valueOf(new Complex(x.ratfix(), y.ratfix()));
    }

    public static ACL2Object complex_rationalp(ACL2Object x)
    {
        return ACL2Symbol.valueOf(x instanceof ACL2Complex);
    }

    public static ACL2Object coerce(ACL2Object x, ACL2Object y)
    {
        if (LIST.equals(y))
        {
            ACL2Object result = ACL2Symbol.NIL;
            if (x instanceof ACL2String)
            {
                String s = ((ACL2String)x).s;
                for (int i = s.length() - 1; i >= 0; i++)
                {
                    result = cons(ACL2Character.intern(s.charAt(i)), result);
                }
            }
            return result;
        } else
        {
            StringBuilder sb = new StringBuilder();
            while (x instanceof ACL2Cons)
            {
                ACL2Cons xc = (ACL2Cons)x;
                char c = 0;
                if (xc.car instanceof ACL2Character)
                {
                    c = ((ACL2Character)xc.car).c;
                }
                sb.append(c);
                x = xc.cdr;
            }
            return new ACL2String(sb.toString());
        }
    }

    public static ACL2Object cons(ACL2Object x, ACL2Object y)
    {
        return new ACL2Cons(x, y);
    }

    public static ACL2Object consp(ACL2Object x)
    {
        return ACL2Symbol.valueOf(x instanceof ACL2Cons);
    }

    public static ACL2Object denominator(ACL2Object x)
    {
        return new ACL2Integer(x instanceof ACL2Rational ? ((ACL2Rational)x).v.d : BigInteger.ONE);
    }

    public static ACL2Object equal(ACL2Object x, ACL2Object y)
    {
        return ACL2Symbol.valueOf(x.equals(y));
    }

    public static ACL2Object ifDef(ACL2Object x, ACL2Object y, ACL2Object z)
    {
        return ACL2Symbol.NIL.equals(x) ? z : y;
    }

    public static ACL2Object imagpart(ACL2Object x)
    {
        if (x instanceof ACL2Complex)
        {
            return ACL2Object.valueOf(((ACL2Complex)x).v.im);
        }
        return ACL2Integer.zero();
    }

    public static ACL2Object integerp(ACL2Object x)
    {
        return ACL2Symbol.valueOf(x instanceof ACL2Integer);
    }

    public static ACL2Object internInPackageOfSymbol(ACL2Object x, ACL2Object y)
    {
        if (x instanceof ACL2String && y instanceof ACL2Symbol)
        {
            return ((ACL2Symbol)y).pkg.getSymbol(((ACL2String)x).s);
        }
        return NIL;
    }

    public static ACL2Object numerator(ACL2Object x)
    {
        if (x instanceof ACL2Integer)
        {
            return x;
        }
        if (x instanceof ACL2Rational)
        {
            return new ACL2Integer(((ACL2Rational)x).v.n);
        }
        return ACL2Integer.zero();
    }

    public static ACL2Object rationalp(ACL2Object x)
    {
        return ACL2Symbol.valueOf(x instanceof ACL2Rational);
    }

    public static ACL2Object realpart(ACL2Object x)
    {
        if (x instanceof ACL2Complex)
        {
            return ACL2Object.valueOf(((ACL2Complex)x).v.re);
        }
        return x.fix();
    }

    public static ACL2Object stringp(ACL2Object x)
    {
        return ACL2Symbol.valueOf(x instanceof ACL2String);
    }

    public static ACL2Object symbol_name(ACL2Object x)
    {
        if (x instanceof ACL2Symbol)
        {
            ACL2Symbol sym = (ACL2Symbol)x;
            return ACL2String.intern(sym.nm, HonsManager.current.get());
        }
        return ACL2Object.emptyStr();
    }

    public static ACL2Object symbol_package_name(ACL2Object x)
    {
        if (x instanceof ACL2Symbol)
        {
            ACL2Symbol sym = (ACL2Symbol)x;
            return ACL2String.intern(sym.pkg.name, HonsManager.current.get());
        }
        return ACL2Object.emptyStr();
    }

    public static ACL2Object symbolp(ACL2Object x)
    {
        return ACL2Symbol.valueOf(x instanceof ACL2Symbol);
    }

    //////////////
    public static ACL2Object booleanp(ACL2Object x)
    {
        return ACL2Object.valueOf(NIL.equals(x) || T.equals(x));
    }

    public static ACL2Object keywordp(ACL2Object x)
    {
        return ACL2Object.valueOf(x instanceof ACL2Symbol && ((ACL2Symbol)x).pkg == ACL2Symbol.KEYWORD);
    }

    public static ACL2Object fix(ACL2Object x)
    {
        return x.isACL2Number() ? x : ACL2Object.valueOf(0);
    }

    public static ACL2Object rfix(ACL2Object x)
    {
        return x instanceof ACL2Rational || x instanceof ACL2Integer ? x : ACL2Object.valueOf(0);
    }

    public static ACL2Object ifix(ACL2Object x)
    {
        return x instanceof ACL2Integer ? x : ACL2Object.valueOf(0);
    }

    public static ACL2Object honscopy(ACL2Object x)
    {
        if (x instanceof ACL2Symbol || x instanceof ACL2Character)
        {
            return x;
        }
        HonsManager hm = HonsManager.current.get();
        return x.honsOwner == hm ? x : x.internImpl(hm);
    }

    public static ACL2Object hons(ACL2Object x, ACL2Object y)
    {
        return ACL2Cons.intern(x, y, HonsManager.current.get());
    }
}
