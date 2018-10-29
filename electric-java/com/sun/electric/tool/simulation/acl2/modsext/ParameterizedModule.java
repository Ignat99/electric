/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ParameterizedModule.java
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
package com.sun.electric.tool.simulation.acl2.modsext;

import com.sun.electric.tool.simulation.acl2.mods.Address;
import com.sun.electric.tool.simulation.acl2.mods.Aliaspair;
import com.sun.electric.tool.simulation.acl2.mods.Assign;
import com.sun.electric.tool.simulation.acl2.mods.Driver;
import com.sun.electric.tool.simulation.acl2.mods.IndexName;
import com.sun.electric.tool.simulation.acl2.mods.Lhatom;
import com.sun.electric.tool.simulation.acl2.mods.Lhrange;
import com.sun.electric.tool.simulation.acl2.mods.Lhs;
import com.sun.electric.tool.simulation.acl2.mods.ModInst;
import com.sun.electric.tool.simulation.acl2.mods.ModName;
import com.sun.electric.tool.simulation.acl2.mods.Module;
import com.sun.electric.tool.simulation.acl2.mods.Name;
import com.sun.electric.tool.simulation.acl2.mods.Path;
import com.sun.electric.tool.simulation.acl2.mods.Util;
import com.sun.electric.tool.simulation.acl2.mods.Wire;
import com.sun.electric.tool.simulation.acl2.mods.Wiretype;
import com.sun.electric.tool.simulation.acl2.modsext.parmods.coretype;
import com.sun.electric.tool.simulation.acl2.modsext.parmods.gate_buf;
import com.sun.electric.tool.simulation.acl2.svex.Svar;
import com.sun.electric.tool.simulation.acl2.svex.Svex;
import com.sun.electric.tool.simulation.acl2.svex.SvexManager;
import com.sun.electric.tool.simulation.acl2.svex.SvexQuote;
import com.sun.electric.tool.simulation.acl2.svex.Vec2;
import com.sun.electric.tool.simulation.acl2.svex.Vec4;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec3Fix;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4BitExtract;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Bitand;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Bitnot;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Bitor;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Bitxor;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Concat;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Equality;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Ite;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4IteStmt;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4PartSelect;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Plus;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4ReductionOr;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4Rsh;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4SignExt;
import com.sun.electric.tool.simulation.acl2.svex.funs.Vec4ZeroExt;
import static com.sun.electric.util.acl2.ACL2.*;
import com.sun.electric.util.acl2.ACL2Object;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameterized SVEX module.
 * Subclasses represent concrete modules.
 */
public abstract class ParameterizedModule
{
    public final String libName;
    public final String modName;
    private final String paramPrefix;
    private final String paramDelim;

    /**
     * Current ModName.
     * There are methods to generate SVEX module for current mod name.
     * These variables are not theread-safe.
     */
    private ModName curModName;
    private String curInstName;

    private Map<String, ACL2Object> params;
    private final Map<String, Name> names = new HashMap<>();

    private SvexManager<Address> sm;
    private final List<Wire> wires = new ArrayList<>();
    private final List<ModInst> insts = new ArrayList<>();
    private final List<Assign<Address>> assigns = new ArrayList<>();
    private final List<Aliaspair<Address>> aliaspairs = new ArrayList<>();

    public ParameterizedModule(String libName, String modName)
    {
        this(libName, modName, "$", "=");
    }

    protected ParameterizedModule(String libName, String modName, String paramPrefix, String paramDelim)
    {
        this.libName = libName;
        this.modName = modName;
        this.paramPrefix = paramPrefix;
        this.paramDelim = paramDelim;
    }

    public static List<ParameterizedModule> getStandardModules()
    {
        List<ParameterizedModule> result = new ArrayList<>();
        result.add(gate_buf.INSTANCE);
        result.add(coretype.INSTANCE);
        return result;
    }

    /**
     * Check if modName is a name of a specialization of this parameterized module
     *
     * @param modName a name of a module specialization
     * @return map from parameter names to parameter values
     */
    protected Map<String, ACL2Object> matchModName(ModName modName)
    {
        if (!modName.isString())
        {
            return null;
        }
        String modNameStr = modName.toString();
        if (!modNameStr.startsWith(this.modName))
        {
            return null;
        }
        String params = modNameStr.substring(this.modName.length());
        Map<String, ACL2Object> parMap = new LinkedHashMap<>();
        while (!params.isEmpty())
        {
            if (!params.startsWith(paramPrefix))
            {
                return null;
            }
            params = params.substring(paramPrefix.length());
            int nextPrefix = params.indexOf(paramPrefix);
            if (nextPrefix < 0)
            {
                nextPrefix = params.length();
            }
            String paramName, paramVal;
            if (paramDelim != null)
            {
                int indDelim = params.lastIndexOf(paramDelim, nextPrefix - paramDelim.length());
                if (indDelim < 0)
                {
                    return null;
                }
                paramName = params.substring(0, indDelim);
                paramVal = params.substring(indDelim + paramDelim.length(), nextPrefix);
            } else
            {
                paramName = "";
                paramVal = params.substring(0, nextPrefix);
            }
            Integer defaultInt = getDefaultInt(paramName);
            if (defaultInt != null)
            {
                parMap.put(paramName, ACL2Object.valueOf(Integer.parseInt(paramVal)));
            } else
            {
                parMap.put(paramName, ACL2Object.valueOf(paramVal));
            }
            params = params.substring(nextPrefix);
        }
        return parMap;
    }

    protected boolean exportsAreStrings()
    {
        return true;
    }

    protected boolean hasState()
    {
        return false;
    }

    /**
     * Check if modName is a specialization of this parameterized module.
     * If it is a specialization then set <code>this.curModName</code>
     * to the <code>modName</code>.
     *
     * @param modName ModName to check
     * @return true if modName is a specialization of this parameterized module.
     */
    public boolean setCurBuilder(ModName modName, SvexManager<Address> sm)
    {
        clear();
        params = matchModName(modName);
        if (params != null)
        {
            curModName = modName;
            this.sm = sm != null ? sm : new SvexManager<>();
            return true;
        }
        curModName = null;
        return false;
    }

    protected void clear()
    {
        sm = null;
        names.clear();
        wires.clear();
        insts.clear();
        assigns.clear();
        aliaspairs.clear();
        curInstName = null;
    }

    public String getModNameStr()
    {
        return modName;
    }

    protected Integer getDefaultInt(String paramName)
    {
        return null;
    }

    /**
     * Return an string value of parameter of current ModName.
     *
     * @param paramName name of a parameter
     * @return integer value of a parameter or null if parameter is absent.
     * @throws NullPointerException if parameter is absent
     * @throws NumberFormatException if parameter is not an integer
     */
    protected int getIntParam(String paramName)
    {
        ACL2Object val = getParam(paramName);
        return val != null ? val.intValueExact() : getDefaultInt(paramName);
    }

    /**
     * Return a string value of parameter of current ModName.
     *
     * @param paramName name of a parameter
     * @return Stringvalue of a parameter or null if parameter is absent.
     */
    protected String getStrParam(String paramName)
    {
        ACL2Object val = getParam(paramName);
        return val != null ? val.stringValueExact() : null;
    }

    protected ACL2Object getParam(String paramName)
    {
        return params.get(paramName);
    }

    private Name getName(String nameStr)
    {
        Name name = names.get(nameStr);
        if (name == null)
        {
            name = Name.fromACL2(honscopy(ACL2Object.valueOf(nameStr)));
            names.put(nameStr, name);
        }
        return name;
    }

    /**
     * Generate a local wire for current modName.
     *
     * @param wireName name of a wire
     * @param width width of wire
     */
    protected void wire(String wireName, int width)
    {
        wire(getName(wireName), width, 0);
    }

    /**
     * Generate a local wire for current modName.
     *
     * @param name name of a wire
     * @param width width of wire
     */
    protected void wire(Name name, int width, int lowIdx)
    {
        wires.add(new Wire(name, width, lowIdx, 0, false, Wiretype.WIRE));
    }

    /**
     * Generate an input for current modName.
     *
     * @param wireName name of an input
     * @param width width of an input
     */
    protected void input(String wireName, int width)
    {
        wire(wireName, width);
    }

    /**
     * Generate a global input for current modName.
     * Examples of global inputs are power supply or global clock.
     *
     * @param wireName name of a global input
     * @param width width of a global input
     */
    protected void global(String wireName, int width)
    {
        wire(wireName, width);
    }

    /**
     * Generate an output for current modName.
     *
     * @param wireName name of an output
     * @param width width of an output
     */
    protected void output(String wireName, int width)
    {
        wire(wireName, width);
    }

    /**
     * Generate an unused export for current modName.
     *
     * @param wireName name of an unused export
     * @param width width of an unused export
     */
    protected void unused(String wireName, int width)
    {
        wire(wireName, width);
    }

    /**
     * Generate a local wire for current modName.
     *
     * @param name name of a wire
     * @param width width of wire
     */
    protected void unused(Name name, int width)
    {
        wire(name, width, 0);
    }

    /**
     * Generate a module instance for current modName.
     *
     * @param instName name of a module instance
     * @param modName ModName of instance prototype.
     */
    protected void instance(String instName, ModName modName)
    {
        insts.add(new ModInst(getName(instName), modName));
        curInstName = instName;
    }

    public void instance(ModName modName, String instName)
    {

    }

    /**
     * Generate a module instance for current modName.
     * Sets current instance for the new instance.
     *
     * @param instName name of a module instance
     * @param modName string name of instance prototype.
     */
    protected void instance(String instName, String modName)
    {
        instance(instName, ModName.fromACL2(ACL2Object.valueOf(modName)));
    }

    /**
     * Generate an Lhrange for current modName.
     * Example: <code>r("din", 3, 1)</code> stands for Verilog name din[3:1].
     *
     * @param wireName name of a local wire
     * @param msb most significant bit
     * @param lsb least significant bit
     * @return Lhrange
     */
    protected Lhrange<Address> r(String wireName, int msb, int lsb)
    {
        return r(getName(wireName), msb, lsb);
    }

    /**
     * Generate an Lhrange for current modName.
     * Example: <code>r("din", 3, 1)</code> stands for Verilog name din[3:1].
     *
     * @param wireName name of a local wire
     * @param msb most significant bit
     * @param lsb least significant bit
     * @return Lhrange
     */
    protected Lhrange<Address> r(Name wireName, int msb, int lsb)
    {
        Path path = Path.simplePath(wireName);
        Address address = Address.valueOf(path);
        Svar<Address> svar = sm.getVar(address);
        return r(svar, msb, lsb);
    }

    /**
     * Generate an Lhrange for current modName.
     * Example: <code>r("inst1", "din", 3, 1)</code> stands for Verilog name inst1.din[3:1].
     *
     * @param instName name of an inatance port
     * @param portName name of a port
     * @param msb most significant bit
     * @param lsb least significant bit
     * @return Lhrange
     */
    protected Lhrange<Address> r(String instName, String portName, int msb, int lsb)
    {
        Path path = Path.makePath(Collections.singletonList(getName(instName)), getName(portName));
        Address address = Address.valueOf(path);
        Svar<Address> svar = sm.getVar(address);
        return r(svar, msb, lsb);
    }

    private Lhrange<Address> r(Svar<Address> svar, int msb, int lsb)
    {
        int width = msb - lsb + 1;
        if (width <= 0)
        {
            throw new IllegalArgumentException();
        }
        return new Lhrange<>(width, Lhatom.valueOf(svar, lsb));
    }

    protected Svex<Address> unfloat(Svex<Address> x)
    {
        return sm.newCall(Vec3Fix.FUNCTION, x);
    }

    protected Svex<Address> uor(Svex<Address> x)
    {
        return sm.newCall(Vec4ReductionOr.FUNCTION, x);
    }

    protected Svex<Address> ite(Svex<Address> test, Svex<Address> th, Svex<Address> el)
    {
        return sm.newCall(Vec4Ite.FUNCTION, test, th, el);
    }

    protected Svex<Address> iteStmt(Svex<Address> test, Svex<Address> th, Svex<Address> el)
    {
        return sm.newCall(Vec4IteStmt.FUNCTION, test, th, el);
    }

    protected Svex<Address> bitnot(Svex<Address> x)
    {
        return sm.newCall(Vec4Bitnot.FUNCTION, x);
    }

    protected Svex<Address> bitnotE(int width, Svex<Address> x)
    {
        return zext(q(width), bitnot(x));
    }

    protected Svex<Address> bitnotE(Svex<Address> x)
    {
        return bitnotE(1, x);
    }

    protected Svex<Address> bitand(Svex<Address> x, Svex<Address> y)
    {
        return sm.newCall(Vec4Bitand.FUNCTION, x, y);
    }

    @SafeVarargs
    protected final Svex<Address> bitandE(int width, Svex<Address>... x)
    {
        Svex<Address> result = null;
        for (Svex<Address> xi : x)
        {
            result = result != null ? zext(q(width), bitand(result, xi)) : xi;
        }
        return result;
    }

    @SafeVarargs
    protected final Svex<Address> bitandE(Svex<Address>... x)
    {
        return bitandE(1, x);
    }

    protected Svex<Address> bitor(Svex<Address> x, Svex<Address> y)
    {
        return sm.newCall(Vec4Bitor.FUNCTION, x, y);
    }

    @SafeVarargs
    protected final Svex<Address> bitorE(int width, Svex<Address>... x)
    {
        Svex<Address> result = null;
        for (Svex<Address> xi : x)
        {
            result = result != null ? zext(q(width), bitor(result, xi)) : xi;
        }
        return result;
    }

    @SafeVarargs
    protected final Svex<Address> bitorE(Svex<Address>... x)
    {
        return bitorE(1, x);
    }

    protected Svex<Address> bitxor(Svex<Address> x, Svex<Address> y)
    {
        return sm.newCall(Vec4Bitxor.FUNCTION, x, y);
    }

    @SafeVarargs
    protected final Svex<Address> bitxorE(int width, Svex<Address>... x)
    {
        Svex<Address> result = null;
        for (Svex<Address> xi : x)
        {
            result = result != null ? zext(q(width), bitxor(result, xi)) : xi;
        }
        return result;
    }

    protected Svex<Address> bitxorE(int width, List<Svex<Address>> x)
    {
        return bitxorE(width, x.toArray(Svex.newSvexArray(x.size())));
    }

    @SafeVarargs
    protected final Svex<Address> bitxorE(Svex<Address>... x)
    {
        return bitxorE(1, x);
    }

    protected Svex<Address> plus(Svex<Address> x, Svex<Address> y)
    {
        return sm.newCall(Vec4Plus.FUNCTION, x, y);
    }

    protected Svex<Address> eq(Svex<Address> x, Svex<Address> y)
    {
        return sm.newCall(Vec4Equality.FUNCTION, x, y);
    }

    protected Svex<Address> concat(Svex<Address> n, Svex<Address> lower, Svex<Address> upper)
    {
        return sm.newCall(Vec4Concat.FUNCTION, n, lower, upper);
    }

    protected Svex<Address> bitExtract(Svex<Address> index, Svex<Address> x)
    {
        return sm.newCall(Vec4BitExtract.FUNCTION, index, x);
    }

    protected Svex<Address> partSelect(Svex<Address> lsb, Svex<Address> width, Svex<Address> x)
    {
        return sm.newCall(Vec4PartSelect.FUNCTION, lsb, width, x);
    }

    protected Svex<Address> rsh(Svex<Address> n, Svex<Address> x)
    {
        return sm.newCall(Vec4Rsh.FUNCTION, n, x);
    }

    protected Svex<Address> zext(Svex<Address> n, Svex<Address> x)
    {
        return sm.newCall(Vec4ZeroExt.FUNCTION, n, x);
    }

    protected Svex<Address> sext(Svex<Address> n, Svex<Address> x)
    {
        return sm.newCall(Vec4SignExt.FUNCTION, n, x);
    }

    protected Svex<Address> q(int val)
    {
        return q(Vec2.valueOf(val));
    }

    protected Svex<Address> q(int upper, int lower)
    {
        return q(Vec4.valueOf(BigInteger.valueOf(upper), BigInteger.valueOf(lower)));
    }

    protected Svex<Address> q(Vec4 val)
    {
        return SvexQuote.valueOf(val);
    }

    protected Svex<Address> v(String wireName)
    {
        return v(wireName, 0);
    }

    protected Svex<Address> v(String wireName, int delay)
    {
        Name name = getName(wireName);
        Path path = Path.simplePath(name);
        Address address = Address.valueOf(path);
        return sm.getSvex(address, delay, false);
    }

    protected Svex<Address> vE(String wireName)
    {
        return zext(q(1), v(wireName, 0));
    }

    /**
     * Generate an assignment of an svex expression to a local wire for current modName
     *
     * @param wireName name of a local wire
     * @param width width of a local wire
     * @param svex SVEX expression
     */
    protected void assign(String wireName, int width, Svex<Address> svex)
    {
        assign(r(wireName, width - 1, 0), svex);
    }

    /**
     * Generate an assignment of an svex expression to a local wire for current modName
     *
     * @param instName name of an instance
     * @param portName name of a port
     * @param width width of a local wire
     * @param svex SVEX expression
     */
    protected void assign(String instName, String portName, int width, Svex<Address> svex)
    {

        assign(r(instName, portName, width - 1, 0), svex);
    }

    /**
     * Generate an assignment of an svex expression to a Lhrange for current modName
     *
     * @param range Lhrange
     * @param svex SVEX expression
     */
    protected void assign(Lhrange<Address> range, Svex<Address> svex)
    {
        assign(new Lhs<>(Arrays.asList(range)), svex);
    }

    /**
     * Generate an assignment of an svex expression to a Lhrange for current modName
     *
     * @param upperRange lower Lhrange
     * @param lowerRange upper Lhrange
     * @param svex SVEX expression
     */
    protected void assign(Lhrange<Address> upperRange, Lhrange<Address> lowerRange, Svex<Address> svex)
    {
        assign(new Lhs<>(Arrays.asList(lowerRange, upperRange)), svex);
    }

    private void assign(Lhs<Address> lhs, Svex<Address> svex)
    {
        Driver<Address> driver = new Driver<>(svex);
        Assign<Address> assign = new Assign<>(lhs, driver);
        assigns.add(assign);
    }

    /**
     * Generate a connection a port of current instance to a list
     * of Lhranges for current ModName.
     *
     * @param portName name of port
     * @param ranges list of Lhranges
     */
    @SafeVarargs
    protected final void conn(String portName, Lhrange<Address>... ranges)
    {
        conn(curInstName, portName, ranges);
    }

    /**
     * Generate a connection of port of current instance to a local wire
     * for current ModName
     *
     * @param portName name of port
     * @param wireName name of local wire
     * @param width width of local wire.
     */
    protected void conn(String portName, String wireName, int width)
    {
        conn(portName, r(wireName, width - 1, 0));
    }

    @SafeVarargs
    private final void conn(String instName, String portName, Lhrange<Address>... ranges)
    {
        int width = 0;
        for (Lhrange<Address> range : ranges)
        {
            width += range.getWidth();
        }
        Lhrange<Address> lrange = r(instName, portName, width - 1, 0);
        conn(lrange, ranges);
    }

    @SafeVarargs
    protected final void conn(Lhrange<Address> lrange, Lhrange<Address>... ranges)
    {
        Lhs<Address> lhs = new Lhs<>(Arrays.asList(lrange));
        Lhs<Address> rhs = new Lhs<>(Arrays.asList(ranges));
        Util.check(lhs.width() == rhs.width());
        Aliaspair<Address> aliaspair = new Aliaspair<>(lhs, rhs);
        aliaspairs.add(aliaspair);
    }

    protected Module<Address> getModule()
    {
        return new Module<>(sm, wires, insts, assigns, aliaspairs);
    }

    protected Module<Address> genModule()
    {
        return null;
    }

    protected String[] genDepModNameStrings()
    {
        return new String[0];
    }

    protected ModName[] genDepModNames()
    {
        String[] modNameStrings = genDepModNameStrings();
        ModName[] result = new ModName[modNameStrings.length];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = ModName.valueOf(modNameStrings[i]);
        }
        return result;
    }

    protected ModInst[] genAllModInsts(ModName[] n)
    {
        Util.check(n.length == 0);
        return new ModInst[0];
    }

    protected int getNumInsts()
    {
        return 0;
    }

    protected int getNumAssigns()
    {
        return 1;
    }

    protected int getTotalInsts()
    {
        return genAllModInsts(genDepModNames()).length;
    }

    protected int getTotalAssigns()
    {
        return 1;
    }

    protected static Lhs<IndexName> aliasWire(List<Lhs<IndexName>> arr, SvexManager<IndexName> sm, int width)
    {
        IndexName name = IndexName.valueOf(arr.size());
        Svar<IndexName> svar = sm.getVar(name);
        Lhatom<IndexName> atom = Lhatom.valueOf(svar);
        Lhrange<IndexName> range = new Lhrange<>(width, atom);
        Lhs<IndexName> lhs = new Lhs<>(Collections.singletonList(range));
        arr.add(lhs);
        return lhs;
    }

    protected static Lhs<IndexName> aliasWire(List<Lhs<IndexName>> arr, SvexManager<IndexName> sm, int width, int offset)
    {
        IndexName name = IndexName.valueOf(arr.size() + offset);
        Svar<IndexName> svar = sm.getVar(name);
        Lhatom<IndexName> atom = Lhatom.valueOf(svar);
        Lhrange<IndexName> range = new Lhrange<>(width, atom);
        Lhs<IndexName> lhs = new Lhs<>(Collections.singletonList(range));
        return lhs;
    }

    protected static Lhs<IndexName> aliasRange(Lhs<IndexName> in, int width, int rsh)
    {
        return in.rsh(rsh).concat(width, Lhs.empty());
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof ParameterizedModule)
        {
            ParameterizedModule that = (ParameterizedModule)o;
            return this.modName.equals(that.modName);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 59 * hash + modName.hashCode();
        return hash;
    }

    @Override
    public String toString()
    {
        return libName.isEmpty() ? modName : libName + "." + modName;
    }
}
