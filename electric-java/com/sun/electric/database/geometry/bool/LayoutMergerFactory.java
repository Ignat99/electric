/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: LayoutMergerFactory.java
 * Written by Dmitry Nadezhin.
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.database.geometry.bool;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.tool.Job;

import java.util.ServiceLoader;

/**
 *
 */
public abstract class LayoutMergerFactory {

    private static LayoutMergerFactory factory;

    /**
     * <p>Constructor for derived classes.</p>
     *
     * <p>The constructor does nothing.</p>
     *
     * <p>Derived classes must create {@link LayoutMergerFactory} objects
     */
    protected LayoutMergerFactory() {
    }

    public abstract LayoutMerger newMerger(Cell topCell);

    public static LayoutMergerFactory getInstance() {
        if (factory == null) {
            try {
                for (LayoutMergerFactory f : ServiceLoader.load(LayoutMergerFactory.class)) {
                    factory = f;
                    break;
                }
            } catch (Error e) {
                System.out.println("Error loading LayoutMergerFactory class via ServiceLoader");
                if (Job.getDebug()) {
                    e.printStackTrace();
                }
            }
            if (factory == null) {
                factory = new LayoutMergerHierImpl.Factory();
//                factory = new LayoutMergerDefaultImpl.Factory();
            }
        }
        return factory;
    }
}
