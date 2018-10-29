/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ImmutablePortInst.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.database;

import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.variable.Variable;

import java.io.IOException;

/**
 * Immutable class which stores varaibles of PortInst.
 */
public class ImmutablePortInst extends ImmutableElectricObject {

    public final static ImmutablePortInst[] NULL_ARRAY = {};
    public final static ImmutablePortInst EMPTY = new ImmutablePortInst(Variable.NULL_ARRAY);

    /** Creates a new instance of ImmutablePortInst */
    private ImmutablePortInst(Variable[] vars) {
        super(vars, 0);
    }

    /**
     * Returns ImmutablePortInst which differs from this ImmutablePortInst by additional Variable.
     * If this ImmutablePortInst has Variable with the same key as new, the old Variable will not be in new
     * ImmutablePortInst.
     * @param var additional Variable.
     * @return ImmutablePortInst with additional Variable.
     * @throws NullPointerException if var is null
     */
    public ImmutablePortInst withVariable(Variable var) {
        Variable[] vars = arrayWithVariable(var.withParam(false).withInherit(false));
        if (getVars() == vars) {
            return this;
        }
        return new ImmutablePortInst(vars);
    }

    /**
     * Returns ImmutablePortInst which differs from this ImmutablePortInst by removing Variable
     * with the specified key. Returns this ImmutablePortInst if it doesn't contain variable with the specified key.
     * @param key Variable Key to remove.
     * @return ImmutablePortInst without Variable with the specified key.
     * @throws NullPointerException if key is null
     */
    public ImmutablePortInst withoutVariable(Variable.Key key) {
        Variable[] vars = arrayWithoutVariable(key);
        if (getVars() == vars) {
            return this;
        }
        if (vars.length == 0) {
            return EMPTY;
        }
        return new ImmutablePortInst(vars);
    }

    /**
     * Returns ImmutablePortInst which differs from this ImmutablePortInst by renamed Ids.
     * @param idMapper a map from old Ids to new Ids.
     * @return ImmutablePortInst with renamed Ids.
     */
    public ImmutablePortInst withRenamedIds(IdMapper idMapper) {
        Variable[] vars = arrayWithRenamedIds(idMapper);
        if (getVars() == vars) {
            return this;
        }
        return new ImmutablePortInst(vars);
    }

    /**
     * Reads optional variable part of ImmutableElectricObject.
     * @param reader where to read.
     */
    static ImmutablePortInst read(IdReader reader) throws IOException {
        return new ImmutablePortInst(readVars(reader));
    }

    /**
     * Return a hash code value for fields of this object.
     * Variables of objects are not compared
     */
    @Override
    public int hashCodeExceptVariables() {
        return 0;
    }

    /**
     * Indicates whether fields of other ImmutableElectricObject are equal to fileds of this object.
     * Variables of objects are not compared.
     * @param o other ImmutableElectricObject.
     * @return true if fields of objects are equal.
     */
    @Override
    public boolean equalsExceptVariables(ImmutableElectricObject o) {
        return (o instanceof ImmutablePortInst);
    }

    /**
     * Checks invariant of this ImmutablePortInst.
     * @throws AssertionError if invariant is broken.
     */
    public void check() {
        super.check(false);
    }
}
