/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Layer.java
 *
 * Copyright (c) 2003, Static Free Software. All rights reserved.
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
package com.sun.electric.technology;

import com.sun.electric.database.EObjectInputStream;
import com.sun.electric.database.EObjectOutputStream;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.id.LayerId;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.GDSLayers.GDSLayerType;
import com.sun.electric.tool.user.GraphicsPreferences;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;

import java.awt.Color;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The Layer class defines a single layer of material, out of which NodeInst and ArcInst objects are created.
 * The Layers are defined by the PrimitiveNode and ArcProto classes, and are used in the generation of geometry.
 * In addition, layers have extra information that is used for output and behavior.
 */
public class Layer implements Serializable, Comparable {

    public static final double DEFAULT_THICKNESS = 0; // 3D default thickness
    public static final double DEFAULT_DISTANCE = 0; // 3D default distance
    /** Describes a P-type layer. */					private static final int PTYPE        =      0100;
    /** Describes a N-type layer. */					private static final int NTYPE        =      0200;
    /** Describes a depletion layer. */					private static final int DEPLETION    =      0400;
    /** Describes a enhancement layer. */				private static final int ENHANCEMENT  =     01000;
    /** Describes a light doped layer. */				private static final int LIGHT        =     02000;
    /** Describes a heavy doped layer. */				private static final int HEAVY        =     04000;
    /** Describes a deep layer. */						private static final int DEEP         =    010000;
    /** Describes a nonelectrical layer. */				private static final int NONELEC      =    020000;
    /** Describes a layer that contacts metal. */		private static final int CONMETAL     =    040000;
    /** Describes a layer that contacts polysilicon. */	private static final int CONPOLY      =   0100000;
    /** Describes a layer that contacts diffusion. */	private static final int CONDIFF      =   0200000;
    /** Describes a layer that is native. */			private static final int NATIVE       =   0400000;
    /** Describes a layer that is VTH or VTL */			private static final int HLVT         =  010000000;
    /** Describes a layer that is inside transistor. */	private static final int INTRANS      =  020000000;
    /** Describes a thick layer. */						private static final int THICK        =  040000000;
    /** Describes a carbon-nanotube Active layer. */	private static final int CARBNANO     = 0100000000;
    /** Describes a interconnect layer. */				private static final int INTERCONNECT = 0200000000;
    /** Describes a cut layer. */						private static final int CUTLAYER     = 0400000000;

    private static final LayerNumbers metalLayers = new LayerNumbers();
    private static final LayerNumbers metalLayers1 = new LayerNumbers();
    private static final LayerNumbers metalLayers2 = new LayerNumbers();
    private static final LayerNumbers metalLayers3 = new LayerNumbers();
    private static final LayerNumbers contactLayers = new LayerNumbers();
    private static final LayerNumbers contactLayers1 = new LayerNumbers();
    private static final LayerNumbers contactLayers2 = new LayerNumbers();
    private static final LayerNumbers contactLayers3 = new LayerNumbers();
    private static final LayerNumbers polyLayers = new LayerNumbers();
    private static List<Function> allFunctions;
    
    public static final String DEFAULT_MASK_NAME = "_MASK_";
    
    /**
     * Get Function Set associated with a given Layer
     * @param layer the layer to find.
     * @return the Function Set associated with the given Layer.
     */
    public static Function.Set getMultiLayersSet(Layer layer) {
        Function.Set thisLayerFunction = (layer.getFunction().isPoly())
                ? new Function.Set(Function.POLY1, Function.GATE)
                : new Function.Set(layer);
        return thisLayerFunction;
    }

    private static class LayerNumbers {

        private final ArrayList<Function> list;
        private int base;

        LayerNumbers() {
            list = new ArrayList<Function>();
            base = 0;
        }

        public void addLayer(Function fun, int level) {
            while (level < base) {
                base--;
                list.add(0, null);
            }
            while (list.size() <= level - base) {
                list.add(null);
            }
            Function oldFunction = list.set(level - base, fun);
            if (oldFunction != null) System.out.println("ERROR: Function " + fun.name + " on level " + level + " exists twice");
            assert oldFunction == null;
        }

        public Function get(int level) {
            return list.get(level - base);
        }
    }

    public int compareTo(Object other) {
        String s = toString();
        String sOther = other.toString();
        return s.compareToIgnoreCase(sOther);
    }
    
    /**
     * Function is a typesafe enum class that describes the function of a layer.
     * Functions are technology-independent and describe the nature of the layer (Metal, Polysilicon, etc.)
     */
    public static enum Function {

        /** Describes an unknown layer. */						UNKNOWN("unknown", 0, 0, 0, 0, 35, 0, 0),
        /** Describes a local interconnect metal layer 2. */	METALNEG2("metal-2-local", -2, 0, 0, 0, 13, 0, 0),
        /** Describes a local interconnect metal layer 1. */	METALNEG1("metal-1-local", -1, 0, 0, 0, 15, 0, 0),

        /** Describes a metal layer 1. */						METAL1   ("metal-1",      1, 0, 0, 0, 17, 0, 0),
        /** Describes a metal layer 1a. */						METAL1C1 ("metal-1-C1",   1, 0, 0, 1, 17, 0, 0),
        /** Describes a metal layer 1b. */						METAL1C2 ("metal-1-C2",   1, 0, 0, 2, 17, 0, 0),
        /** Describes a metal layer 1c. */						METAL1C3 ("metal-1-C3",   1, 0, 0, 3, 17, 0, 0),
        /** Describes a metal layer 2. */						METAL2   ("metal-2",      2, 0, 0, 0, 19, 0, 0),
        /** Describes a metal layer 2a. */						METAL2C1 ("metal-2-C1",   2, 0, 0, 1, 19, 0, 0),
        /** Describes a metal layer 2b. */						METAL2C2 ("metal-2-C2",   2, 0, 0, 2, 19, 0, 0),
        /** Describes a metal layer 2c. */						METAL2C3 ("metal-2-C3",   2, 0, 0, 3, 19, 0, 0),
        /** Describes a metal layer 3. */						METAL3   ("metal-3",      3, 0, 0, 0, 21, 0, 0),
        /** Describes a metal layer 3a. */						METAL3C1 ("metal-3-C1",   3, 0, 0, 1, 21, 0, 0),
        /** Describes a metal layer 3b. */						METAL3C2 ("metal-3-C2",   3, 0, 0, 2, 21, 0, 0),
        /** Describes a metal layer 3c. */						METAL3C3 ("metal-3-C3",   3, 0, 0, 3, 21, 0, 0),
        /** Describes a metal layer 4. */						METAL4   ("metal-4",      4, 0, 0, 0, 23, 0, 0),
        /** Describes a metal layer 4a. */						METAL4C1 ("metal-4-C1",   4, 0, 0, 1, 23, 0, 0),
        /** Describes a metal layer 4b. */						METAL4C2 ("metal-4-C2",   4, 0, 0, 2, 23, 0, 0),
        /** Describes a metal layer 4c. */						METAL4C3 ("metal-4-C3",   4, 0, 0, 3, 23, 0, 0),
        /** Describes a metal layer 5. */						METAL5   ("metal-5",      5, 0, 0, 0, 25, 0, 0),
        /** Describes a metal layer 5a. */						METAL5C1 ("metal-5-C1",   5, 0, 0, 1, 25, 0, 0),
        /** Describes a metal layer 5b. */						METAL5C2 ("metal-5-C2",   5, 0, 0, 2, 25, 0, 0),
        /** Describes a metal layer 5c. */						METAL5C3 ("metal-5-C3",   5, 0, 0, 3, 25, 0, 0),
        /** Describes a metal layer 6. */						METAL6   ("metal-6",      6, 0, 0, 0, 27, 0, 0),
        /** Describes a metal layer 6a. */						METAL6C1 ("metal-6-C1",   6, 0, 0, 1, 27, 0, 0),
        /** Describes a metal layer 6b. */						METAL6C2 ("metal-6-C2",   6, 0, 0, 2, 27, 0, 0),
        /** Describes a metal layer 6c. */						METAL6C3 ("metal-6-C3",   6, 0, 0, 3, 27, 0, 0),
        /** Describes a metal layer 7. */						METAL7   ("metal-7",      7, 0, 0, 0, 29, 0, 0),
        /** Describes a metal layer 7a. */						METAL7C1 ("metal-7-C1",   7, 0, 0, 1, 29, 0, 0),
        /** Describes a metal layer 7b. */						METAL7C2 ("metal-7-C2",   7, 0, 0, 2, 29, 0, 0),
        /** Describes a metal layer 7c. */						METAL7C3 ("metal-7-C3",   7, 0, 0, 3, 29, 0, 0),
        /** Describes a metal layer 8. */						METAL8   ("metal-8",      8, 0, 0, 0, 31, 0, 0),
        /** Describes a metal layer 8a. */						METAL8C1 ("metal-8-C1",   8, 0, 0, 1, 31, 0, 0),
        /** Describes a metal layer 8b. */						METAL8C2 ("metal-8-C2",   8, 0, 0, 2, 31, 0, 0),
        /** Describes a metal layer 8c. */						METAL8C3 ("metal-8-C3",   8, 0, 0, 3, 31, 0, 0),
        /** Describes a metal layer 9. */						METAL9   ("metal-9",      9, 0, 0, 0, 33, 0, 0),
        /** Describes a metal layer 9a. */						METAL9C1 ("metal-9-C1",   9, 0, 0, 1, 33, 0, 0),
        /** Describes a metal layer 9b. */						METAL9C2 ("metal-9-C2",   9, 0, 0, 2, 33, 0, 0),
        /** Describes a metal layer 9c. */						METAL9C3 ("metal-9-C3",   9, 0, 0, 3, 33, 0, 0),
        /** Describes a metal layer 10. */						METAL10  ("metal-10",    10, 0, 0, 0, 35, 0, 0),
        /** Describes a metal layer 10a. */						METAL10C1("metal-10-C1", 10, 0, 0, 1, 35, 0, 0),
        /** Describes a metal layer 10b. */						METAL10C2("metal-10-C2", 10, 0, 0, 2, 35, 0, 0),
        /** Describes a metal layer 10c. */						METAL10C3("metal-10-C3", 10, 0, 0, 3, 35, 0, 0),
        /** Describes a metal layer 11. */						METAL11  ("metal-11",    11, 0, 0, 0, 37, 0, 0),
        /** Describes a metal layer 11a. */						METAL11C1("metal-11-C1", 11, 0, 0, 1, 37, 0, 0),
        /** Describes a metal layer 11b. */						METAL11C2("metal-11-C2", 11, 0, 0, 2, 37, 0, 0),
        /** Describes a metal layer 11c. */						METAL11C3("metal-11-C3", 11, 0, 0, 3, 37, 0, 0),
        /** Describes a metal layer 12. */						METAL12  ("metal-12",    12, 0, 0, 0, 39, 0, 0),
        /** Describes a metal layer 12a. */						METAL12C1("metal-12-C1", 12, 0, 0, 1, 39, 0, 0),
        /** Describes a metal layer 12b. */						METAL12C2("metal-12-C2", 12, 0, 0, 2, 39, 0, 0),
        /** Describes a metal layer 12c. */						METAL12C3("metal-12-C3", 12, 0, 0, 3, 39, 0, 0),
        /** Describes a metal layer 13. */						METAL13  ("metal-13",    13, 0, 0, 0, 41, 0, 0),
        /** Describes a metal layer 13a. */						METAL13C1("metal-13-C1", 13, 0, 0, 1, 41, 0, 0),
        /** Describes a metal layer 13b. */						METAL13C2("metal-13-C2", 13, 0, 0, 2, 41, 0, 0),
        /** Describes a metal layer 13c. */						METAL13C3("metal-13-C3", 13, 0, 0, 3, 41, 0, 0),
        /** Describes a metal layer 14. */						METAL14  ("metal-14",    14, 0, 0, 0, 43, 0, 0),
        /** Describes a metal layer 14a. */						METAL14C1("metal-14-C1", 14, 0, 0, 1, 43, 0, 0),
        /** Describes a metal layer 14b. */						METAL14C2("metal-14-C2", 14, 0, 0, 2, 43, 0, 0),
        /** Describes a metal layer 14c. */						METAL14C3("metal-14-C3", 14, 0, 0, 3, 43, 0, 0),
        
        /** Describes a polysilicon layer 1. */					POLY1("poly-1", 0, 0, 1, 0, 12, 0, 0),
        /** Describes a polysilicon layer 2. */					POLY2("poly-2", 0, 0, 2, 0, 13, 0, 0),
        /** Describes a polysilicon layer 3. */					POLY3("poly-3", 0, 0, 3, 0, 14, 0, 0),
        /** Describes a polysilicon gate layer. */				GATE("gate", 0, 0, 0, 0, 15, INTRANS, 0),
        /** Describes a diffusion layer. */						DIFF("diffusion", 0, 0, 0, 0, 11, 0, 0),
        /** Describes a P-diffusion layer. */					DIFFP("p-diffusion", 0, 0, 0, 0, 11, PTYPE, 0),
        /** Describes a N-diffusion layer. */					DIFFN("n-diffusion", 0, 0, 0, 0, 11, NTYPE, 0),
        /** Describes a N-diffusion carbon nanotube layer. */	DIFFNCN("n-diffusion-cn", 0, 0, 0, 0, 11, NTYPE | CARBNANO, 0),
        /** Describes a P-diffusion carbon nanotube layer. */	DIFFPCN("n-diffusion-cn", 0, 0, 0, 0, 11, NTYPE | CARBNANO, 0),
        /** Describes an implant layer. */						IMPLANT("implant", 0, 0, 0, 0, 2, 0, 0),
        /** Describes a P-implant layer. */						IMPLANTP("p-implant", 0, 0, 0, 0, 2, PTYPE, 0),
        /** Describes an N-implant layer. */					IMPLANTN("n-implant", 0, 0, 0, 0, 2, NTYPE, 0),
        
        /** Describes a contact layer 1. */						CONTACT1   ("contact-1",     0, 1,  0, 0, 16, 0, 0),
        /** Describes a contact layer 1a. */					CONTACT1C1 ("contact-1-C1",  0, 1,  0, 1, 16, 0, 0),
        /** Describes a contact layer 1b. */					CONTACT1C2 ("contact-1-C2",  0, 1,  0, 2, 16, 0, 0),
        /** Describes a contact layer 1c. */					CONTACT1C3 ("contact-1-C3",  0, 1,  0, 3, 16, 0, 0),
        /** Describes a contact layer 2. */						CONTACT2   ("contact-2",     0, 2,  0, 0, 18, 0, 0),
        /** Describes a contact layer 2a. */					CONTACT2C1 ("contact-2-C1",  0, 2,  0, 1, 18, 0, 0),
        /** Describes a contact layer 2b. */					CONTACT2C2 ("contact-2-C2",  0, 2,  0, 2, 18, 0, 0),
        /** Describes a contact layer 2c. */					CONTACT2C3 ("contact-2-C3",  0, 2,  0, 3, 18, 0, 0),
        /** Describes a contact layer 3. */						CONTACT3   ("contact-3",     0, 3,  0, 0, 20, 0, 0),
        /** Describes a contact layer 3a. */					CONTACT3C1 ("contact-3-C1",  0, 3,  0, 1, 20, 0, 0),
        /** Describes a contact layer 3b. */					CONTACT3C2 ("contact-3-C2",  0, 3,  0, 2, 20, 0, 0),
        /** Describes a contact layer 3c. */					CONTACT3C3 ("contact-3-C3",  0, 3,  0, 3, 20, 0, 0),
        /** Describes a contact layer 4. */						CONTACT4   ("contact-4",     0, 4,  0, 0, 22, 0, 0),
        /** Describes a contact layer 4a. */					CONTACT4C1 ("contact-4-C1",  0, 4,  0, 1, 22, 0, 0),
        /** Describes a contact layer 4b. */					CONTACT4C2 ("contact-4-C2",  0, 4,  0, 2, 22, 0, 0),
        /** Describes a contact layer 4c. */					CONTACT4C3 ("contact-4-C3",  0, 4,  0, 3, 22, 0, 0),
        /** Describes a contact layer 5. */						CONTACT5   ("contact-5",     0, 5,  0, 0, 24, 0, 0),
        /** Describes a contact layer 5a. */					CONTACT5C1 ("contact-5-C1",  0, 5,  0, 1, 24, 0, 0),
        /** Describes a contact layer 5b. */					CONTACT5C2 ("contact-5-C2",  0, 5,  0, 2, 24, 0, 0),
        /** Describes a contact layer 5c. */					CONTACT5C3 ("contact-5-C3",  0, 5,  0, 3, 24, 0, 0),
        /** Describes a contact layer 6. */						CONTACT6   ("contact-6",     0, 6,  0, 0, 26, 0, 0),
        /** Describes a contact layer 6a. */					CONTACT6C1 ("contact-6-C1",  0, 6,  0, 1, 26, 0, 0),
        /** Describes a contact layer 6b. */					CONTACT6C2 ("contact-6-C2",  0, 6,  0, 2, 26, 0, 0),
        /** Describes a contact layer 6c. */					CONTACT6C3 ("contact-6-C3",  0, 6,  0, 3, 26, 0, 0),
        /** Describes a contact layer 7. */						CONTACT7   ("contact-7",     0, 7,  0, 0, 28, 0, 0),
        /** Describes a contact layer 7a. */					CONTACT7C1 ("contact-7-C1",  0, 7,  0, 1, 28, 0, 0),
        /** Describes a contact layer 7b. */					CONTACT7C2 ("contact-7-C2",  0, 7,  0, 2, 28, 0, 0),
        /** Describes a contact layer 7c. */					CONTACT7C3 ("contact-7-C3",  0, 7,  0, 3, 28, 0, 0),
        /** Describes a contact layer 8. */						CONTACT8   ("contact-8",     0, 8,  0, 0, 30, 0, 0),
        /** Describes a contact layer 8a. */					CONTACT8C1 ("contact-8-C1",  0, 8,  0, 1, 30, 0, 0),
        /** Describes a contact layer 8b. */					CONTACT8C2 ("contact-8-C2",  0, 8,  0, 2, 30, 0, 0),
        /** Describes a contact layer 8c. */					CONTACT8C3 ("contact-8-C3",  0, 8,  0, 3, 30, 0, 0),
        /** Describes a contact layer 9. */						CONTACT9   ("contact-9",     0, 9,  0, 0, 32, 0, 0),
        /** Describes a contact layer 9a. */					CONTACT9C1 ("contact-9-C1",  0, 9,  0, 1, 32, 0, 0),
        /** Describes a contact layer 9b. */					CONTACT9C2 ("contact-9-C2",  0, 9,  0, 2, 32, 0, 0),
        /** Describes a contact layer 9c. */					CONTACT9C3 ("contact-9-C3",  0, 9,  0, 3, 32, 0, 0),
        /** Describes a contact layer 10. */					CONTACT10  ("contact-10",    0, 10, 0, 0, 34, 0, 0),
        /** Describes a contact layer 10a. */					CONTACT10C1("contact-10-C1", 0, 10, 0, 1, 34, 0, 0),
        /** Describes a contact layer 10b. */					CONTACT10C2("contact-10-C2", 0, 10, 0, 2, 34, 0, 0),
        /** Describes a contact layer 10c. */					CONTACT10C3("contact-10-C3", 0, 10, 0, 3, 34, 0, 0),
        /** Describes a contact layer 11. */					CONTACT11  ("contact-11",    0, 11, 0, 0, 36, 0, 0),
        /** Describes a contact layer 11a. */					CONTACT11C1("contact-11-C1", 0, 11, 0, 1, 36, 0, 0),
        /** Describes a contact layer 11b. */					CONTACT11C2("contact-11-C2", 0, 11, 0, 2, 36, 0, 0),
        /** Describes a contact layer 11c. */					CONTACT11C3("contact-11-C3", 0, 11, 0, 3, 36, 0, 0),
        /** Describes a contact layer 12. */					CONTACT12  ("contact-12",    0, 12, 0, 0, 38, 0, 0),
        /** Describes a contact layer 12a. */					CONTACT12C1("contact-12-C1", 0, 12, 0, 1, 38, 0, 0),
        /** Describes a contact layer 12b. */					CONTACT12C2("contact-12-C2", 0, 12, 0, 2, 38, 0, 0),
        /** Describes a contact layer 12c. */					CONTACT12C3("contact-12-C3", 0, 12, 0, 3, 38, 0, 0),
        /** Describes a contact layer 13. */					CONTACT13  ("contact-13",    0, 13, 0, 0, 40, 0, 0),
        /** Describes a contact layer 13a. */					CONTACT13C1("contact-13-C1", 0, 13, 0, 1, 40, 0, 0),
        /** Describes a contact layer 13b. */					CONTACT13C2("contact-13-C2", 0, 13, 0, 2, 40, 0, 0),
        /** Describes a contact layer 13c. */					CONTACT13C3("contact-13-C3", 0, 13, 0, 3, 40, 0, 0),
        /** Describes a contact layer 14. */					CONTACT14  ("contact-14",    0, 14, 0, 0, 42, 0, 0),
        /** Describes a contact layer 14a. */					CONTACT14C1("contact-14-C1", 0, 14, 0, 1, 42, 0, 0),
        /** Describes a contact layer 14b. */					CONTACT14C2("contact-14-C2", 0, 14, 0, 2, 42, 0, 0),
        /** Describes a contact layer 14c. */					CONTACT14C3("contact-14-C3", 0, 14, 0, 3, 42, 0, 0),
        
        /** Describes a sinker (diffusion-to-buried plug). */	PLUG("plug", 0, 0, 0, 0, 40, 0, 0),
        /** Describes an overglass layer (passivation). */		OVERGLASS("overglass", 0, 0, 0, 0, 41, 0, 0),
        /** Describes a resistor layer. */						RESISTOR("resistor", 0, 0, 0, 0, 4, 0, 0),
        /** Describes a capacitor layer. */						CAP("capacitor", 0, 0, 0, 0, 5, 0, 0),
        /** Describes a transistor layer. */					TRANSISTOR("transistor", 0, 0, 0, 0, 3, 0, 0),
        /** Describes an emitter of bipolar transistor. */		EMITTER("emitter", 0, 0, 0, 0, 6, 0, 0),
        /** Describes a base of bipolar transistor. */			BASE("base", 0, 0, 0, 0, 7, 0, 0),
        /** Describes a collector of bipolar transistor. */		COLLECTOR("collector", 0, 0, 0, 0, 8, 0, 0),
        /** Describes a substrate layer. */						SUBSTRATE("substrate", 0, 0, 0, 0, 1, 0, 0),
        /** Describes a well layer. */		 					WELL("well", 0, 0, 0, 0, 0, 0, 0),
        /** Describes a P-well layer. */						WELLP("p-well", 0, 0, 0, 0, 0, PTYPE, 0),
        /** Describes a N-well layer. */						WELLN("n-well", 0, 0, 0, 0, 0, NTYPE, 0),
        /** Describes a guard layer. */							GUARD("guard", 0, 0, 0, 0, 9, 0, 0),
        /** Describes an isolation layer (bipolar). */			ISOLATION("isolation", 0, 0, 0, 0, 10, 0, 0),
        /** Describes a bus layer. */							BUS("bus", 0, 0, 0, 0, 42, 0, 0),
        /** Describes an artwork layer. */						ART("art", 0, 0, 0, 0, 43, 0, 0),
        /** Describes a control layer. */						CONTROL("control", 0, 0, 0, 0, 44, 0, 0),
        /** Describes a tileNot layer. */						TILENOT("tileNot", 0, 0, 0, 0, 45, 0, 0),
        /** Describes a dummy polysilicon layer 1 */			DMYPOLY1("dmy-poly-1", 0, 0, 0, 0, POLY1.getHeight(), 0, 0),
        /** Describes a dummy polysilicon layer 2 */			DMYPOLY2("dmy-poly-2", 0, 0, 0, 0, POLY2.getHeight(), 0, 0),
        /** Describes a dummy polysilicon layer 3 */			DMYPOLY3("dmy-poly-3", 0, 0, 0, 0, POLY3.getHeight(), 0, 0),
        /** Describes a dummy diffusion layer */				DMYDIFF("dmy-diffusion", 0, 0, 0, 0, DIFF.getHeight(), 0, 0),
        
        /** Describes a dummy metal layer 1 */					DMYMETAL1("dmy-metal-1", 0, 0, 0, 0, METAL1.getHeight(), 0, 1),
        /** Describes a dummy metal layer 2 */					DMYMETAL2("dmy-metal-2", 0, 0, 0, 0, METAL2.getHeight(), 0, 2),
        /** Describes a dummy metal layer 3 */					DMYMETAL3("dmy-metal-3", 0, 0, 0, 0, METAL3.getHeight(), 0, 3),
        /** Describes a dummy metal layer 4 */					DMYMETAL4("dmy-metal-4", 0, 0, 0, 0, METAL4.getHeight(), 0, 4),
        /** Describes a dummy metal layer 5 */					DMYMETAL5("dmy-metal-5", 0, 0, 0, 0, METAL5.getHeight(), 0, 5),
        /** Describes a dummy metal layer 6 */					DMYMETAL6("dmy-metal-6", 0, 0, 0, 0, METAL6.getHeight(), 0, 6),
        /** Describes a dummy metal layer 7 */					DMYMETAL7("dmy-metal-7", 0, 0, 0, 0, METAL7.getHeight(), 0, 7),
        /** Describes a dummy metal layer 8 */					DMYMETAL8("dmy-metal-8", 0, 0, 0, 0, METAL8.getHeight(), 0, 8),
        /** Describes a dummy metal layer 9 */					DMYMETAL9("dmy-metal-9", 0, 0, 0, 0, METAL9.getHeight(), 0, 9),
        /** Describes a dummy metal layer 10 */					DMYMETAL10("dmy-metal-10", 0, 0, 0, 0, METAL10.getHeight(), 0, 10),
        /** Describes a dummy metal layer 11 */					DMYMETAL11("dmy-metal-11", 0, 0, 0, 0, METAL11.getHeight(), 0, 11),
        /** Describes a dummy metal layer 12 */					DMYMETAL12("dmy-metal-12", 0, 0, 0, 0, METAL12.getHeight(), 0, 12),
        /** Describes a dummy metal layer 13 */					DMYMETAL13("dmy-metal-13", 0, 0, 0, 0, METAL13.getHeight(), 0, 13),
        /** Describes a dummy metal layer 14 */					DMYMETAL14("dmy-metal-14", 0, 0, 0, 0, METAL14.getHeight(), 0, 14),
        
        /** Describes a exclusion polysilicon layer 1 */		DEXCLPOLY1("dexcl-poly-1", 0, 0, 0, 0, POLY1.getHeight(), 0, 0),
        /** Describes a exclusion polysilicon layer 2 */		DEXCLPOLY2("dexcl-poly-2", 0, 0, 0, 0, POLY2.getHeight(), 0, 0),
        /** Describes a exclusion polysilicon layer 3 */		DEXCLPOLY3("dexcl-poly-3", 0, 0, 0, 0, POLY3.getHeight(), 0, 0),
        /** Describes a exclusion diffusion layer */			DEXCLDIFF("dexcl-diffusion", 0, 0, 0, 0, DIFF.getHeight(), 0, 0),
        
        /** Describes a exclusion metal layer 1 */				DEXCLMETAL1("dexcl-metal-1", 0, 0, 0, 0, METAL1.getHeight(), 0, 1),
        /** Describes a exclusion metal layer 2 */				DEXCLMETAL2("dexcl-metal-2", 0, 0, 0, 0, METAL2.getHeight(), 0, 2),
        /** Describes a exclusion metal layer 3 */				DEXCLMETAL3("dexcl-metal-3", 0, 0, 0, 0, METAL3.getHeight(), 0, 3),
        /** Describes a exclusion metal layer 4 */				DEXCLMETAL4("dexcl-metal-4", 0, 0, 0, 0, METAL4.getHeight(), 0, 4),
        /** Describes a exclusion metal layer 5 */				DEXCLMETAL5("dexcl-metal-5", 0, 0, 0, 0, METAL5.getHeight(), 0, 5),
        /** Describes a exclusion metal layer 6 */				DEXCLMETAL6("dexcl-metal-6", 0, 0, 0, 0, METAL6.getHeight(), 0, 6),
        /** Describes a exclusion metal layer 7 */				DEXCLMETAL7("dexcl-metal-7", 0, 0, 0, 0, METAL7.getHeight(), 0, 7),
        /** Describes a exclusion metal layer 8 */				DEXCLMETAL8("dexcl-metal-8", 0, 0, 0, 0, METAL8.getHeight(), 0, 8),
        /** Describes a exclusion metal layer 9 */				DEXCLMETAL9("dexcl-metal-9", 0, 0, 0, 0, METAL9.getHeight(), 0, 9),
        /** Describes a exclusion metal layer 10 */				DEXCLMETAL10("dexcl-metal-10", 0, 0, 0, 0, METAL10.getHeight(), 0, 10),
        /** Describes a exclusion metal layer 11 */				DEXCLMETAL11("dexcl-metal-11", 0, 0, 0, 0, METAL11.getHeight(), 0, 11),
        /** Describes a exclusion metal layer 12 */				DEXCLMETAL12("dexcl-metal-12", 0, 0, 0, 0, METAL12.getHeight(), 0, 12),
        /** Describes a exclusion metal layer 13 */				DEXCLMETAL13("dexcl-metal-13", 0, 0, 0, 0, METAL13.getHeight(), 0, 13),
        /** Describes a exclusion metal layer 14 */				DEXCLMETAL14("dexcl-metal-14", 0, 0, 0, 0, METAL14.getHeight(), 0, 14);
        
        /** Describes a depletion layer. */
        public static final int DEPLETION = Layer.DEPLETION;
        /** Describes a enhancement layer. */
        public static final int ENHANCEMENT = Layer.ENHANCEMENT;
        /** Describes a light doped layer. */
        public static final int LIGHT = Layer.LIGHT;
        /** Describes a heavy doped layer. */
        public static final int HEAVY = Layer.HEAVY;
        /** Describes a interconnect layer. */												
        public static final int INTERCONNECT = Layer.INTERCONNECT;
        /** Describes a non-electrical layer (does not carry signals). */
        public static final int NONELEC = Layer.NONELEC;
        /** Describes a layer that contacts metal (used to identify contacts/vias). */
        public static final int CONMETAL = Layer.CONMETAL;
        /** Describes a layer that contacts polysilicon (used to identify contacts). */
        public static final int CONPOLY = Layer.CONPOLY;
        /** Describes a layer that contacts diffusion (used to identify contacts). */
        public static final int CONDIFF = Layer.CONDIFF;
        /** Describes a layer that is VTH or VTL */
        public static final int HLVT = Layer.HLVT;
        /** Describes a thick layer. */
        public static final int THICK = Layer.THICK;
        /** Describes a native layer. */
        public static final int NATIVE = Layer.NATIVE;
        /** Describes a deep layer. */
        public static final int DEEP = Layer.DEEP;
        /** Describes a cut layer. */
        public static final int CUTLAYER = Layer.CUTLAYER;

        private final String name;
        private final boolean isMetal;
        private final boolean isContact;
        private final boolean isPoly;
        private final int level;
        private final int maskColor;
        private final int height;
        private final int extraBits;
        private static final int[] extras = {PTYPE, NTYPE, DEPLETION, ENHANCEMENT, LIGHT, HEAVY, INTERCONNECT, NONELEC,
        	CONMETAL, CONPOLY, CONDIFF, HLVT, INTRANS, THICK, CARBNANO, CUTLAYER};

        static {
            allFunctions = Arrays.asList(Function.class.getEnumConstants());
        }

        private Function(String name, int metalLevel, int contactLevel, int polyLevel, int maskColor, int height, int extraBits, int genericLevel) {
            this.name = name;
            this.height = height;
            this.extraBits = extraBits;
            this.maskColor = maskColor;
            isMetal = metalLevel != 0;
            isContact = contactLevel != 0;
            isPoly = polyLevel != 0;
            int level = 0;
            if (genericLevel != 0) {
                level = genericLevel;
            }
            if (isMetal) {
            	switch (maskColor)
            	{
            		case 0: metalLayers.addLayer(this, level = metalLevel);    break;
            		case 1: metalLayers1.addLayer(this, level = metalLevel);   break;
            		case 2: metalLayers2.addLayer(this, level = metalLevel);   break;
            		case 3: metalLayers3.addLayer(this, level = metalLevel);   break;
            	}
            }
            if (isContact) {
            	switch (maskColor)
            	{
            		case 0: contactLayers.addLayer(this, level = contactLevel);    break;
            		case 1: contactLayers1.addLayer(this, level = contactLevel);   break;
            		case 2: contactLayers2.addLayer(this, level = contactLevel);   break;
            		case 3: contactLayers3.addLayer(this, level = contactLevel);   break;
            	}
            }
            if (isPoly) {
                polyLayers.addLayer(this, level = polyLevel);
            }
            this.level = level;
        }

    	public static Function findFunction(String userName)
    	{
			try
			{
				Function val = valueOf(userName);
				return val;
			}
			catch (Exception e)
			{
				System.out.println("Error: can't find User value for Function called '" + userName + ";");
				return null;
			}
    	}
    	
        /**
         * Returns a printable version of this Function.
         * @return a printable version of this Function.
         */
        public String toString() {
            String toStr = name;
            for (int i = 0; i < extras.length; i++) {
                if ((extraBits & extras[i]) == 0) {
                    continue;
                }
                toStr += "," + getExtraName(extras[i]);
            }
            return toStr;
        }

        /**
         * Returns the name for this Function.
         * @return the name for this Function.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the constant name for this Function.
         * Constant names are used when writing Java code, so they must be the same as the actual symbol name.
         * @return the constant name for this Function.
         */
        public String getConstantName() {
            return name();
        }

        /**
         * Method to return a list of all Layer Functions.
         * @return a list of all Layer Functions.
         */
        public static List<Function> getFunctions() {
            return allFunctions;
        }

        /**
         * Method to return an array of the Layer Function "extra bits".
         * @return an array of the Layer Function "extra bits".
         * Each entry in the array is a single "extra bit", but they can be ORed together to combine them.
         */
        public static int[] getFunctionExtras() {
            return extras;
        }

        /**
         * Method to convert an "extra bits" value to a name.
         * @param extra the extra bits value (must be a single bit, not an ORed combination).
         * @return the name of that extra bit.
         */
        public static String getExtraName(int extra)
        {
            if (extra == PTYPE)        return "p-type";
            if (extra == NTYPE)        return "n-type";
            if (extra == DEPLETION)    return "depletion";
            if (extra == ENHANCEMENT)  return "enhancement";
            if (extra == LIGHT)        return "light";
            if (extra == HEAVY)        return "heavy";
			if (extra == INTERCONNECT) return "interconnect";
            if (extra == NONELEC)      return "nonelectrical";
            if (extra == CONMETAL)     return "connects-metal";
            if (extra == CONPOLY)      return "connects-poly";
            if (extra == CONDIFF)      return "connects-diff";
            if (extra == HLVT)         return "vt";
            if (extra == INTRANS)      return "inside-transistor";
            if (extra == THICK)        return "thick";
            if (extra == NATIVE)       return "native";
            if (extra == DEEP)         return "deep";
            if (extra == CARBNANO)     return "carb-nano";
            if (extra == CUTLAYER)     return "cut-layer";
            return "";
        }

        /**
         * Method to convert an "extra bits" name to its numeric value.
         * @param name the name of the bit.
         * @return the numeric equivalent of that bit.
         */
        public static int parseExtraName(String name) {
            if (name.equalsIgnoreCase("p-type"))            return PTYPE;
            if (name.equalsIgnoreCase("n-type"))            return NTYPE;
            if (name.equalsIgnoreCase("depletion"))         return DEPLETION;
            if (name.equalsIgnoreCase("enhancement"))       return ENHANCEMENT;
            if (name.equalsIgnoreCase("light"))             return LIGHT;
            if (name.equalsIgnoreCase("heavy"))             return HEAVY;
			if (name.equalsIgnoreCase("interconnect"))      return INTERCONNECT;
            if (name.equalsIgnoreCase("nonelectrical"))     return NONELEC;
            if (name.equalsIgnoreCase("connects-metal"))    return CONMETAL;
            if (name.equalsIgnoreCase("connects-poly"))     return CONPOLY;
            if (name.equalsIgnoreCase("connects-diff"))     return CONDIFF;
            if (name.equalsIgnoreCase("vt"))                return HLVT;
            if (name.equalsIgnoreCase("inside-transistor")) return INTRANS;
            if (name.equalsIgnoreCase("thick"))             return THICK;
            if (name.equalsIgnoreCase("native"))            return NATIVE;
            if (name.equalsIgnoreCase("deep"))              return DEEP;
            if (name.equalsIgnoreCase("carb-nano"))         return CARBNANO;
            if (name.equalsIgnoreCase("cut-layer"))         return CUTLAYER;
            return 0;
        }

        /**
         * Method to convert an "extra bits" value to a constant name.
         * Constant names are used when writing Java code, so they must be the same as the actual symbol name.
         * @param extra the extra bits value (must be a single bit, not an ORed combination).
         * @return the name of that extra bit's constant.
         */
        public static String getExtraConstantName(int extra)
        {
            if (extra == PTYPE)        return "PTYPE";
            if (extra == NTYPE)        return "NTYPE";
            if (extra == DEPLETION)    return "DEPLETION";
            if (extra == ENHANCEMENT)  return "ENHANCEMENT";
            if (extra == LIGHT)        return "LIGHT";
            if (extra == HEAVY)        return "HEAVY";
			if (extra == INTERCONNECT) return "INTERCONNECT";
            if (extra == NONELEC)      return "NONELEC";
            if (extra == CONMETAL)     return "CONMETAL";
            if (extra == CONPOLY)      return "CONPOLY";
            if (extra == CONDIFF)      return "CONDIFF";
            if (extra == HLVT)         return "HLVT";
            if (extra == INTRANS)      return "INTRANS";
            if (extra == THICK)        return "THICK";
            if (extra == NATIVE)       return "NATIVE";
            if (extra == DEEP)         return "DEEP";
            if (extra == CARBNANO)     return "CN";
            if (extra == CUTLAYER)     return "CUT";
            return "";
        }

        /**
         * Method to get the level of this Layer.
         * The level applies to metal and polysilicon functions, and gives the layer number
         * (i.e. Metal-2 is level 2).
         * @return the level of this Layer.
         */
        public int getLevel() {
            return level;
        }

        /**
         * Method to get the mask color of this Layer.
         * Some layers require multiple masks to fabricate (double or triple patterning).
         * @return the mask color of this Layer (0 if not colored).
         */
        public int getMaskColor() {
            return maskColor;
        }

        /**
         * Method to determine if a given layer is colored (info via mask value).
         * @return true if layer is colored.
         */
        public boolean isColored() { return getMaskColor() > 0; }
        
        /**
         * Method to find the Function that corresponds to Metal on a given layer.
         * @param level the layer (starting at 1 for Metal-1).
         * @return the Function that represents that Metal layer. Null if the given layer level is invalid.
         */
        public static Function getMetal(int level) {
        	return getMetal(level, 0);
        }

        /**
         * Method to find the Function that corresponds to Metal on a given layer and given mask color.
         * @param level the layer (starting at 1 for Metal-1).
         * @param maskColor the mask number (maximum is 3; 0 for uncolored layers)
         * @return the Function that represents that Metal layer. Null if the given layer level is invalid.
         */
        public static Function getMetal(int level, int maskColor) {
            if (level > EGraphics.getMaxTransparentLayer()) {
                System.out.println("Invalid metal layer level:" + level);
                return null;
            }
            switch (maskColor)
            {
            	case 0: return metalLayers.get(level);
            	case 1: return metalLayers1.get(level);
            	case 2: return metalLayers2.get(level);
            	case 3: return metalLayers3.get(level);
            }
            return null;
        }

        /**
         * Method to find the Function that corresponds to Dummy Metal on a given layer.
         * @param level the layer (starting at 0 for Metal-1).
         * @return the Function that represents that Metal layer. Null if the given layer level is invalid.
         */
        public static Function getDummyMetal(int level) {
            if (level > EGraphics.getMaxTransparentLayer()) {
                System.out.println("Invalid metal layer level:" + level);
                return null;
            }

            switch (level) {
                case 0:
                    return (Layer.Function.DMYMETAL1);
                case 1:
                    return (Layer.Function.DMYMETAL2);
                case 2:
                    return (Layer.Function.DMYMETAL3);
                case 3:
                    return (Layer.Function.DMYMETAL4);
                case 4:
                    return (Layer.Function.DMYMETAL5);
                case 5:
                    return (Layer.Function.DMYMETAL6);
                case 6:
                    return (Layer.Function.DMYMETAL7);
                case 7:
                    return (Layer.Function.DMYMETAL8);
                case 8:
                    return (Layer.Function.DMYMETAL9);
                case 9:
                    return (Layer.Function.DMYMETAL10);
                case 10:
                    return (Layer.Function.DMYMETAL11);
                case 11:
                    return (Layer.Function.DMYMETAL12);
                case 12:
                    return (Layer.Function.DMYMETAL13);
                case 13:
                    return (Layer.Function.DMYMETAL14);
            }
            // Should never reach this point
            return null;
        }

        /**
         * Method to find the Function that corresponds to Dummy Exclusion Metal on a given layer.
         * @param l the layer (starting at 0 for Metal-1).
         * @return the Function that represents that Metal layer. Null if the given layer level is invalid.
         */
        public static Function getDummyExclMetal(int l) {
            if (l > EGraphics.getMaxTransparentLayer()) {
                System.out.println("Invalid metal layer level:" + l);
                return null;
            }

            switch (l) {
                case 0:
                    return (Layer.Function.DEXCLMETAL1);
                case 1:
                    return (Layer.Function.DEXCLMETAL2);
                case 2:
                    return (Layer.Function.DEXCLMETAL3);
                case 3:
                    return (Layer.Function.DEXCLMETAL4);
                case 4:
                    return (Layer.Function.DEXCLMETAL5);
                case 5:
                    return (Layer.Function.DEXCLMETAL6);
                case 6:
                    return (Layer.Function.DEXCLMETAL7);
                case 7:
                    return (Layer.Function.DEXCLMETAL8);
                case 8:
                    return (Layer.Function.DEXCLMETAL9);
                case 9:
                    return (Layer.Function.DEXCLMETAL10);
                case 10:
                    return (Layer.Function.DEXCLMETAL11);
                case 11:
                    return (Layer.Function.DEXCLMETAL12);
                case 12:
                    return (Layer.Function.DEXCLMETAL13);
                case 13:
                    return (Layer.Function.DEXCLMETAL14);
            }
            // Should never reach this point
            return null;
        }

        /**
         * Method to find the Function that corresponds to a contact on a given layer.
         * @param l the layer (starting at 1 for Contact-1).
         * @param maskColor the mask number (maximum is 3; 0 for uncolored layers)
         * @return the Function that represents that Contact layer. Null if the given layer level is invalid.
         */
        public static Function getContact(int l, int maskColor) {
            if (l > EGraphics.getMaxTransparentLayer()) {
                System.out.println("Invalid via layer level:" + l);
                return null;
            }
            switch (maskColor)
            {
            	case 0: return contactLayers.get(l);
            	case 1: return contactLayers1.get(l);
            	case 2: return contactLayers2.get(l);
            	case 3: return contactLayers3.get(l);
            }
            return null;
        }

        /**
         * Method to find the Function that corresponds to Polysilicon on a given layer.
         * @param l the layer (starting at 1 for Polysilicon-1).
         * @return the Function that represents that Polysilicon layer.
         */
        public static Function getPoly(int l) {
            Function func = polyLayers.get(l);
            return func;
        }

        /**
         * Method to tell whether this layer function is metal.
         * @return true if this layer function is metal.
         */
        public boolean isMetal() {
            return isMetal;
        }

        /**
         * Method to tell whether this layer function is diffusion (active).
         * @return true if this layer function is diffusion (active).
         */
        public boolean isDiff() {
            if (this == DIFF || this == DIFFP || this == DIFFN || this == DIFFNCN || this == DIFFPCN) {
                return true;
            }
            return false;
        }

        /**
         * Method to tell whether this layer function is polysilicon.
         * @return true if this layer function is polysilicon.
         */
        public boolean isPoly() {
            return isPoly || this == GATE;
        }

        ;

		/**
		 * Method to tell whether this layer function is polysilicon in the gate of a transistor.
		 * @return true if this layer function is the gate of a transistor.
		 */
		public boolean isGatePoly() {
            if (isPoly() && (extraBits & INTRANS) != 0) {
                return true;
            }
            return false;
        }

        /**
         * Method to tell whether this layer function is a contact.
         * @return true if this layer function is contact.
         */
        public boolean isContact() {
            return isContact;
        }

        /**
         * Method to tell whether this layer function is a well.
         * @return true if this layer function is a well.
         */
        public boolean isWell() {
            if (this == WELL || this == WELLP || this == WELLN) {
                return true;
            }
            return false;
        }

        /**
         * Method to tell whether this layer function is substrate.
         * @return true if this layer function is substrate.
         */
        public boolean isSubstrate() {
            if (this == SUBSTRATE
                    || this == WELL || this == WELLP || this == WELLN
                    || this == IMPLANT || this == IMPLANTN || this == IMPLANTP) {
                return true;
            }
            return false;
        }

        /**
         * Method to tell whether this layer function is implant.
         * @return true if this layer function is implant.
         */
        public boolean isImplant() {
            return (this == IMPLANT || this == IMPLANTN || this == IMPLANTP);
        }

        /**
         * Method to tell whether this layer function is a dummy
         * @return true if this layer function is a dummy
         */
        public boolean isDummy() {
            return (this == DMYDIFF || this == DMYPOLY1 || this == DMYPOLY2 || this == DMYPOLY3
                    || this == DMYMETAL1 || this == DMYMETAL2 || this == DMYMETAL3 || this == DMYMETAL4
                    || this == DMYMETAL5 || this == DMYMETAL6 || this == DMYMETAL7 || this == DMYMETAL8
                    || this == DMYMETAL9 || this == DMYMETAL10 || this == DMYMETAL11 || this == DMYMETAL12
                    || this == DMYMETAL13 || this == DMYMETAL14);
        }

        /**
         * Method to tell whether this layer function is a dummy exclusion
         * @return true if this layer function is a dummy exclusion
         */
        public boolean isDummyExclusion() {
            return (this == DEXCLDIFF || this == DEXCLPOLY1 || this == DEXCLPOLY2 || this == DEXCLPOLY3
                    || this == DEXCLMETAL1 || this == DEXCLMETAL2 || this == DEXCLMETAL3 || this == DEXCLMETAL4
                    || this == DEXCLMETAL5 || this == DEXCLMETAL6 || this == DEXCLMETAL7 || this == DEXCLMETAL8
                    || this == DEXCLMETAL9 || this == DEXCLMETAL10 || this == DEXCLMETAL11 || this == DEXCLMETAL12
                    || this == DEXCLMETAL13 || this == DEXCLMETAL14);
        }

        /**
         * Method to tell whether this layer function is in subset
         * of layer functions restricted by specified number
         * of metals and polysilicons.
         * @param numMetals number of metals in subset.
         * @param numPolys number of polysilicons in subset
         * @return true if this layer function is in subset.
         */
        public boolean isUsed(int numMetals, int numPolys) {
            if (isMetal || isContact || isDummyExclusion()) {
                return level <= numMetals;
            } else if (isPoly) {
                return level <= numPolys;
            } else {
                return true;
            }
        }

        /**
         * Method to tell the distance of this layer function.
         * @return the distance of this layer function.
         */
        public int getHeight() {
            return height;
        }

        /**
         * A set of Layer.Functions
         */
        public static class Set {

            final BitSet bits = new BitSet();
            int extraBits;
            /** Set if all Layer.Functions */
            public static final Set ALL = new Set(Function.class.getEnumConstants());

            /**
             * Constructs Function.Set from a Layer
             * @param l Layer
             */
            public Set(Layer l) {
                bits.set(l.getFunction().ordinal());
                extraBits = l.getFunctionExtras();
            }

            /**
             * Constructs Function.Set from varargs Functions.
             * @param funs variable list of Functions.
             */
            public Set(Function... funs) {
                for (Function f : funs) {
                    bits.set(f.ordinal());
                }
                this.extraBits = NO_FUNCTION_EXTRAS; // same value as Layer.extraFunctions
            }

            /**
             * Constructs Function.Set from a collection of Functions.
             * @param funs a Collection of Functions.
             */
            public Set(Collection<Function> funs) {
                for (Function f : funs) {
                    bits.set(f.ordinal());
                }
                this.extraBits = NO_FUNCTION_EXTRAS; // same value as Layer.extraFunctions;
            }

            public void add(Layer l) {
                bits.set(l.getFunction().ordinal());
                extraBits |= l.getFunctionExtras();
            }

            /**
             * Returns true if specified Functions is in this Set.
             * @param f Function to test.
             * @param extraFunction
             * @return true if specified Functions is in this Set.
             */
            public boolean contains(Function f, int extraFunction) {
                // Check first if there is a match in the extra bits
                int extra = extraBits & extraFunction;
                boolean extraBitsM = extraFunction == NO_FUNCTION_EXTRAS || (extra != 0);
                return extraBitsM && bits.get(f.ordinal());
            }
        }
    }

    /**
     * Method to determine level of Layer2 with respect to Layer1.
     * Positive if Layer2 is above Layer1.
     * @param l1 the first Layer.
     * @param l2 the second Layer.
     * @return relationship of layers.
     */
    public static int getNeighborLevel(Layer l1, Layer l2) {
        int level1 = l1.getFunction().getLevel();
        int level2 = l2.getFunction().getLevel();
        return level2 - level1;
    }
    
    /***************************************************************************************************
     * Layer Comparators
     ***************************************************************************************************/
    
    /**
     * Different ways to sort layers
     */
    public static enum LayerSortingType 
    {
    	ByName(new LayerSortByName()), 
    	ByGDSIndex(new LayerSortByGDSIndex()), 
    	ByOrderInTechFile(null), // provide elements in the same order they are defined in tech file
    	ByHeight(new LayerHeight(false)), 
    	ByHeightContact(new LayerHeight(true)), 
    	ByFunctionLevel(new LayerSortByFunctionLevel()), 
    	ByDepth(new LayersByDepth()),;
    
    	Comparator<Layer> layerFunction = null;
    	LayerSortingType(Comparator<Layer> f)
    	{
    		layerFunction = f;
    	}
    	
    	/**
    	 * Method to find a LayerSortingType based on its name.
    	 * It catches the exception if the string doesn't match with any given type;
    	 * returns ByOrderInTechFile in the failure case.
    	 * @param userName
    	 * @return the requested LayerSortingType.
    	 */
    	public static LayerSortingType findType(String userName)
    	{
			try
			{
				LayerSortingType val = valueOf(userName);
				return val;
			}
			catch (Exception e)
			{
				System.out.println("Error: can't find User value for LayerSorting called '" + userName + ";");
				return LayerSortingType.ByOrderInTechFile;
			}
    	}
    }
    
    /*********************/
    /** ByFunctionLevel **/
    /*********************/
    /**
     * Comparator class for sorting Layers by their name.
     * It has to be public due to usage in a fill job function
     */
    private static class LayerSortByFunctionLevel implements Comparator<Layer> {

        /**
         * Method to compare two layers by their name.
         * @param l1 one layer.
         * @param l2 another layer.
         * @return an integer indicating their sorting order.
         */
        public int compare(Layer l1, Layer l2) 
        {
        	// Algorithm taken from VectorCache
			int level1 = 1000, level2 = 1000;
			boolean isContact1 = false;
			boolean isContact2 = false;
			if (l1 != null)
			{
				Layer.Function fun = l1.getFunction();
				level1 = fun.getLevel();
				isContact1 = fun.isContact();
			}
			if (l2 != null)
			{
				Layer.Function fun = l2.getFunction();
				level2 = fun.getLevel();
				isContact2 = fun.isContact();
			}
			if (isContact1 != isContact2)
				return isContact1 ? -1 : 1;
			return level1 - level2;
        	/*
            int level1 = l1.getFunction().getLevel();
            int level2 = l2.getFunction().getLevel();
            return level1 - level2;
            */
        }

//        public static boolean areNeightborLayers(Layer l1, Layer l2)
//        {
//            int level1 = l1.getFunction().getLevel();
//            int level2 = l2.getFunction().getLevel();
//            return Math.abs(getNeighborLevel(l1, l2)) <=1;
//        }
//        public static int getNeighborLevel(Layer l1, Layer l2) {
//            int level1 = l1.getFunction().getLevel();
//            int level2 = l2.getFunction().getLevel();
//            return level2 - level1;
//        }
    }

    /*********************/
    /** ByDepth         **/
    /*********************/
    /**
	 * Comparator class for sorting Layers by their depth.
	 */
    private static class LayersByDepth implements Comparator<Layer>
	{
		/**
		 * Method to sort LayerSort by their height.
		 */
		public int compare(Layer l1, Layer l2)
		{
			double diff = l1.getDepth() - l2.getDepth();
			if (diff == 0.0) return 0;
			if (diff < 0.0) return -1;
			return 1;
		}
	}
    
    /*********************/
    /** ByName          **/
    /*********************/
    /**
     * Comparator class for sorting Layers by their name.
     */
    private static class LayerSortByName implements Comparator<Layer> {

        /**
         * Method to compare two layers by their name.
         * @param l1 one layer.
         * @param l2 another layer.
         * @return an integer indicating their sorting order.
         */
        public int compare(Layer l1, Layer l2) {
            String s1 = l1.getName().toLowerCase();
            String s2 = l2.getName().toLowerCase();
            return TextUtils.STRING_NUMBER_ORDER.compare(s1, s2);
        }
    }
    
    /*********************/
    /** ByGDSIndex      **/
    /*********************/
    /**
     * Comparator class for sorting Layers by their GDS index
     */
    private static class LayerSortByGDSIndex implements Comparator<Layer> {

        /**
         * Method to compare two layers by their name.
         * @param l1 one layer.
         * @param l2 another layer.
         * @return an integer indicating their sorting order.
         */
        public int compare(Layer l1, Layer l2) {
    		EDatabase database = EDatabase.clientDatabase();
    		Map<Setting,Object> context = database.getSettings();
        	// Might not be the most efficient way
        	Foundry t1 = l1.getTechnology().getSelectedFoundry();
        	Foundry t2 = l2.getTechnology().getSelectedFoundry();
        	assert(t1 == t2);
        	Setting s1 = t1.getGDSLayerSetting(l1);
        	Setting s2 = t2.getGDSLayerSetting(l2);
        	GDSLayers n1 = GDSLayers.parseLayerString(context.get(s1).toString());
        	GDSLayers n2 = GDSLayers.parseLayerString(context.get(s2).toString());
            int int1 = (n1 != null) ? n1.getLayerNumber(GDSLayerType.DRAWING) : 0; 
            int int2 = (n2 != null) ? n2.getLayerNumber(GDSLayerType.DRAWING) : 0;
            return int1 - int2;
        }
    }
    
    /*********************/
    /** ByHeight        **/
    /*********************/
    /**
     * Class to make a sorted list of layers in this Technology.
     * The list is sorted by depth (from bottom to top) stored in Function.height.
     */
    private static class LayerHeight implements Comparator<Layer> {

        final boolean liftContacts;

        private LayerHeight(boolean liftContacts) {
            this.liftContacts = liftContacts;
        }

        public int compare(Layer l1, Layer l2) {
            Layer.Function f1 = l1.getFunction();
            Layer.Function f2 = l2.getFunction();
            if (f1 == null || f2 == null) {
                System.out.println();
            }

            int h1 = f1.getHeight();
            int h2 = f2.getHeight();
            if (liftContacts) {
                if (f1.isContact()) {
                    h1++;
                } else if (f1.isMetal()) {
                    h1--;
                }
                if (f2.isContact()) {
                    h2++;
                } else if (f2.isMetal()) {
                    h2--;
                }
            }
            int cmp = h1 - h2;
            if (cmp != 0) {
                return cmp;
            }
            Technology tech1 = l1.getTechnology();
            Technology tech2 = l2.getTechnology();
            if (tech1 != tech2) {
                int techIndex1 = tech1 != null ? tech1.getId().techIndex : -1;
                int techIndex2 = tech2 != null ? tech2.getId().techIndex : -1;
                return techIndex1 - techIndex2;
            }
            return l1.getIndex() - l2.getIndex();
        }
    }
    
    /**
     * Function to sort list of layers based on LayerSortingType
     * @param layerList List of Layers.
     * @param type LayerSortingType
     * @return Sorted list based on the LayerSortingType criterion
     */
    public static List<Layer> getLayersSortedByRule(List<Layer> layerList, LayerSortingType type)
    {
    	assert(type.layerFunction != null);
    	Collections.sort(layerList, type.layerFunction);
        return (layerList);
    }
    
    /**
     * Method to return a sorted list of layers based on user's preference
     * @param tech technology associated to layers
     * @param layerList List of Layers.
     * @param type LayerSortingType
     * @return Sorted list of layers based on user's preference
     */
    public static List<Layer> getLayersSortedByUserPreference(Technology tech, List<Layer> layerList, LayerSortingType type)
    {
        if (type != LayerSortingType.ByOrderInTechFile) // not based on definition in tech file
        	return getLayersSortedByRule(layerList, type);

        // keep same order as in the tech file
    	List<Layer> tmp = new ArrayList<Layer>(layerList.size());
    	for (Iterator<Layer> it = tech.getLayers(); it.hasNext();) 
    	{
    		Layer l = it.next();
    		if (layerList.contains(l))
    			tmp.add(l);
    	}
        return tmp;
    }
    
    /***************************************************************************************************
     * End of Layer Comparators
     ***************************************************************************************************/
    
    private final LayerId layerId;
    private int index = -1; // contains index in technology or -1 for standalone layers
    private final Technology tech;
    private EGraphics factoryGraphics;
    private Function function;
    private static final int NO_FUNCTION_EXTRAS = 0;
    private int functionExtras;
    private Setting cifLayerSetting;
    private Setting dxfLayerSetting;
    private Setting skillLayerSetting;
    private Setting resistanceSetting;
    private Setting capacitanceSetting;
    private Setting edgeCapacitanceSetting;
    private Setting layer3DThicknessSetting;
    private Setting layer3DDistanceSetting;
    /** the pure-layer node that contains just this layer */
    private PrimitiveNode pureLayerNode;

    private Layer(String name, Technology tech, EGraphics graphics) {
        layerId = tech.getId().newLayerId(name);
        this.tech = tech;
        if (graphics == null) {
            throw new NullPointerException();
        }
        this.factoryGraphics = graphics;

        this.function = Function.UNKNOWN;
    }

    protected Object writeReplace() {
        return new LayerKey(this);
    }

    private static class LayerKey extends EObjectInputStream.Key<Layer> {

        public LayerKey() {
        }

        private LayerKey(Layer layer) {
            super(layer);
        }

        @Override
        public void writeExternal(EObjectOutputStream out, Layer layer) throws IOException {
            out.writeObject(layer.getTechnology());
            out.writeInt(layer.getId().chronIndex);
        }

        @Override
        public Layer readExternal(EObjectInputStream in) throws IOException, ClassNotFoundException {
            Technology tech = (Technology) in.readObject();
            int chronIndex = in.readInt();
            Layer layer = tech.getLayerByChronIndex(chronIndex);
            if (layer == null) {
                throw new InvalidObjectException("arc proto not found");
            }
            return layer;
        }
    }

    /**
     * Method to create a new layer with the given name and graphics.
     * @param tech the Technology that this layer belongs to.
     * @param name the name of the layer.
     * @param graphics the appearance of the layer.
     * @return the Layer object.
     */
    public static Layer newInstance(Technology tech, String name, EGraphics graphics) {
        if (tech == null) {
            throw new NullPointerException();
        }
        int transparent = graphics.getTransparentLayer();
        if (transparent != 0) {
            Color colorFromMap = tech.getFactoryTransparentLayerColors()[transparent - 1];
            if ((colorFromMap.getRGB() & 0xFFFFFF) != graphics.getRGB()) {
                throw new IllegalArgumentException();
            }
        }
        Layer layer = new Layer(name, tech, graphics);
        tech.addLayer(layer);
        return layer;
    }

    /**
     * Method to return the Id of this Layer.
     * @return the Id of this Layer.
     */
    public LayerId getId() {
        return layerId;
    }

    /**
     * Method to return the name of this Layer.
     * @return the name of this Layer.
     */
    public String getName() {
        return layerId.name;
    }

    /**
     * Method to return the full name of this Layer.
     * Full name has format "techName:layerName"
     * @return the full name of this Layer.
     */
    public String getFullName() {
        return layerId.fullName;
    }

    /**
     * Method to return the index of this Layer.
     * The index is 0-based.
     * @return the index of this Layer.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Method to set the index of this Layer.
     * The index is 0-based.
     * @param index the index of this Layer.
     */
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * Method to return the Technology of this Layer.
     * @return the Technology of this Layer.
     */
    public Technology getTechnology() {
        return tech;
    }

    /**
     * Method to set the graphics description of this Layer.
     * @param graphics graphics description of this Layer.
     */
    public void setGraphics(EGraphics graphics) {
        UserInterfaceMain.setGraphicsPreferences(UserInterfaceMain.getGraphicsPreferences().withGraphics(this, graphics));
    }

    /**
     * Method to return the graphics description of this Layer.
     * @return the graphics description of this Layer.
     */
    public EGraphics getGraphics() {
    	GraphicsPreferences gra = UserInterfaceMain.getGraphicsPreferences();
        return (gra != null ? gra.getGraphics(this) : null);
    }

    /**
     * Method to return the graphics description of this Layer by factory default.
     * @return the factory graphics description of this Layer.
     */
    public EGraphics getFactoryGraphics() {
        return factoryGraphics;
    }

    /**
     * Method to set the Function of this Layer.
     * @param function the Function of this Layer.
     */
    public void setFunction(Function function) {
        this.function = function;
        this.functionExtras = NO_FUNCTION_EXTRAS;
    }

    /**
     * Method to set the Function of this Layer when the function is complex.
     * Some layer functions have extra bits of information to describe them.
     * For example, P-Type Diffusion has the Function DIFF but the extra bits PTYPE.
     * @param function the Function of this Layer.
     * @param functionExtras extra bits to describe the Function of this Layer.
     */
    public void setFunction(Function function, int functionExtras) {
        this.function = function;
        int numBits = 0;
        for (int i = 0; i < 32; i++) {
            if ((functionExtras & (1 << i)) != 0) {
                numBits++;
            }
        }
        if (numBits >= 2
                && functionExtras != (DEPLETION | HEAVY) && functionExtras != (DEPLETION | LIGHT)
                && functionExtras != (ENHANCEMENT | HEAVY) && functionExtras != (ENHANCEMENT | LIGHT)
                || numBits == 1 && Function.getExtraConstantName(functionExtras).length() == 0) {
            throw new IllegalArgumentException("functionExtras=" + Integer.toHexString(functionExtras));
        }
        this.functionExtras = functionExtras;
    }

    /**
     * Method to return the Function of this Layer.
     * @return the Function of this Layer.
     */
    public Function getFunction() {
        return function;
    }

    /**
     * Method to return the Function "extras" of this Layer.
     * The "extras" are a set of modifier bits, such as "p-type".
     * @return the Function extras of this Layer.
     */
    public int getFunctionExtras() {
        return functionExtras;
    }

    /**
     * Method to set the Pure Layer Node associated with this Layer.
     * @param pln the Pure Layer PrimitiveNode to use for this Layer.
     */
    public void setPureLayerNode(PrimitiveNode pln) {
        pureLayerNode = pln;
    }

    /**
     * Method to make the Pure Layer Node associated with this Layer.
     * @param nodeName the name of the PrimitiveNode.
     * Primitive names may not contain unprintable characters, spaces, tabs, a colon (:), semicolon (;) or curly braces ({}).
     * @param size the width and the height of the PrimitiveNode.
     * @param style the Poly.Type this PrimitiveNode will generate (polygon, cross, etc.).
     * @return the Pure Layer PrimitiveNode to use for this Layer.
     */
    public PrimitiveNode makePureLayerNode(String nodeName, double size, Poly.Type style, String portName, ArcProto... connections) {
        PrimitiveNode pln = new PrimitiveNode.Polygonal(nodeName, tech, EPoint.ORIGIN, size, size, ERectangle.ORIGIN,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(this, 0, style, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
                        new Technology.TechPoint(EdgeH.r(0), EdgeV.t(0))
                    })
                });
        pln.addPrimitivePorts(
                new PrimitivePort.Polygonal(pln, connections, portName, true, 0, 180, 0,
                EdgeH.l(0), EdgeV.b(0), EdgeH.r(0), EdgeV.t(0)));
        pln.setFunction(PrimitiveNode.Function.NODE);
        pureLayerNode = pln;
        return pln;
    }

    /**
     * Method to return the Pure Layer Node associated with this Layer.
     * @return the Pure Layer Node associated with this Layer.
     */
    public PrimitiveNode getPureLayerNode() {
        return pureLayerNode;
    }

    /**
     * Method to tell whether this layer function is non-electrical.
     * Non-electrical layers do not carry any signal (for example, artwork, text).
     * @return true if this layer function is non-electrical.
     */
    public boolean isNonElectrical() {
        return (functionExtras & Function.NONELEC) != 0;
    }

    /**
     * Method to determine if the layer function corresponds to a diffusion layer.
     * Used in parasitic calculation
     * @return true if this Layer is diffusion.
     */
    public boolean isDiffusionLayer() {
        return getFunction().isDiff();
    }

    /**
     * Method to determine if the layer corresponds to a VT layer. Used in DRC
     * @return true if this layer is a VT layer.
     */
    public boolean isVTImplantLayer() {
        return (function.isImplant() && (functionExtras & Layer.Function.HLVT) != 0);
    }

    /**
     * Method to determine if the layer corresponds to a poly cut layer. Used in 3D View
     * @return true if this layer is a poly cut layer.
     */
    public boolean isPolyCutLayer() {
        return (function.isContact() && (functionExtras & Layer.Function.CONPOLY) != 0);
    }

    /**
     * Method to determine if the layer corresponds to a poly cut layer. Used in 3D View
     * @return true if this layer is a poly cut layer.
     */
    public boolean isCarbonNanotubeLayer() {
        return (functionExtras & Layer.CARBNANO) != 0;
    }

    /**
     * Function to determine if two metal layers belong to the same
     * metal group. For example, uncolored M1 and colored M1 (mask1).
     * @param other layer to check group
     * @return true if both layers are in the same group.
     */
    public boolean areLayersInSameMetalGroup(Layer other)
    {
    	Function f1 = getFunction();
    	Function f2 = other.getFunction();
    	
    	if (!f1.isMetal() || !f2.isMetal()) return false; // not all metals
    	
    	return f1.getLevel() == f2.getLevel();
    }
    
    /******************** Setting ****************************************************/
    private Setting makeLayerSetting(String what, String factory) {
        String techName = tech.getTechName();
        return getSubNode(what).makeStringSetting(what + "LayerFor" + getName() + "IN" + techName,
                Technology.TECH_NODE,
                getName(), what + " tab", what + " for layer " + getName() + " in technology " + techName, factory);
    }

    private Setting makeParasiticSetting(String what, double factory) {
        return getSubNode(what).makeDoubleSetting(what + "ParasiticFor" + getName() + "IN" + tech.getTechName(),
                Technology.TECH_NODE,
                getName(), "Parasitic tab", "Technology " + tech.getTechName() + ", " + what + " for layer " + getName(), factory);
    }

    private Setting make3DSetting(String what, double factory) {
        factory = DBMath.round(factory);
        return getSubNode(what).makeDoubleSetting(what + "Of" + getName() + "IN" + tech.getTechName(),
                Technology.TECH_NODE,
                getName(), "3D tab", "Technology " + tech.getTechName() + ", 3D " + what + " for layer " + getName(), factory);
    }

    private Setting.Group getSubNode(String type) {
        return tech.getProjectSettings().node(type);
    }

    /**
     * Method to set the 3D distance and thickness of this Layer.
     * @param thickness the thickness of this layer.
     * @param distance the distance of this layer above the ground plane (silicon).
     * Negative values represent layes in silicon like p++, p well, etc.
     */
    public void setFactory3DInfo(double thickness, double distance) {
        thickness = DBMath.round(thickness);
        distance = DBMath.round(distance);
        // We don't call setDistance and setThickness directly here due to reflection code.
        layer3DDistanceSetting = make3DSetting("Distance", distance);
        layer3DThicknessSetting = make3DSetting("Thickness", thickness);
    }

    /**
     * Method to return the distance of this layer, by default.
     * The higher the distance value, the farther from the wafer.
     * @return the distance of this layer above the ground plane, by default.
     */
    public double getDistance() {
        return layer3DDistanceSetting.getDouble();
    }

    /**
     * Returns project preferences to tell the distance of this layer.
     * @return project preferences to tell the distance of this layer.
     */
    public Setting getDistanceSetting() {
        return layer3DDistanceSetting;
    }

    /**
     * Method to return the thickness of this layer, by default.
     * Layers can have a thickness of 0, which causes them to be rendered flat.
     * @return the distance of this layer above the ground plane, by default.
     */
    public double getThickness() {
        return layer3DThicknessSetting.getDouble();
    }

    /**
     * Returns project preferences to tell the thickness of this layer.
     * @return project preferences to tell the thickness of this layer.
     */
    public Setting getThicknessSetting() {
        return layer3DThicknessSetting;
    }

    /**
     * Method to calculate Z value of the upper part of the layer.
     * Note: not called getHeight to avoid confusion
     * with getDistance())
     * Don't call distance+thickness because those are factory values.
     * @return Depth of the layer
     */
    public double getDepth() {
        return DBMath.round(getDistance() + getThickness());
    }

    /**
     * Method to set the factory-default CIF name of this Layer.
     * @param cifLayer the factory-default CIF name of this Layer.
     */
    public void setFactoryCIFLayer(String cifLayer) {
        cifLayerSetting = makeLayerSetting("CIF", cifLayer);
    }

    /**
     * Method to return the CIF name of this layer.
     * @return the CIF name of this layer.
     */
    public String getCIFLayer() {
        return cifLayerSetting.getString();
    }

    /**
     * Returns project preferences to tell the CIF name of this Layer.
     * @return project preferences to tell the CIF name of this Layer.
     */
    public Setting getCIFLayerSetting() {
        return cifLayerSetting;
    }
    
    /**
     * Method to set the factory-default DXF name of this Layer.
     * @param dxfLayer the factory-default DXF name of this Layer.
     */
    public void setFactoryDXFLayer(String dxfLayer) {
        dxfLayerSetting = makeLayerSetting("DXF", dxfLayer);
    }

    /**
     * Method to return the DXF name of this layer.
     * @return the DXF name of this layer.
     */
    public String getDXFLayer() {
        if (dxfLayerSetting == null) {
            return "";
        }
        return dxfLayerSetting.getString();
    }

    /**
     * Returns project preferences to tell the DXF name of this Layer.
     * @return project preferences to tell the DXF name of this Layer.
     */
    public Setting getDXFLayerSetting() {
        return dxfLayerSetting;
    }

    /**
     * Method to set the factory-default Skill name of this Layer.
     * @param skillLayer the factory-default Skill name of this Layer.
     */
    public void setFactorySkillLayer(String skillLayer) {
        skillLayerSetting = makeLayerSetting("Skill", skillLayer);
    }

    /**
     * Method to return the Skill name of this layer.
     * @return the Skill name of this layer.
     */
    public String getSkillLayer() {
        return skillLayerSetting.getString();
    }

    /**
     * Returns project preferences to tell the Skill name of this Layer.
     * @return project preferences to tell the Skill name of this Layer.
     */
    public Setting getSkillLayerSetting() {
        return skillLayerSetting;
    }

    /**
     * Method to set the Spice parasitics for this Layer.
     * This is typically called only during initialization.
     * It does not set the "option" storage, as "setResistance()",
     * "setCapacitance()", and ""setEdgeCapacitance()" do.
     * @param resistance the resistance of this Layer.
     * @param capacitance the capacitance of this Layer.
     * @param edgeCapacitance the edge capacitance of this Layer.
     */
    public void setFactoryParasitics(double resistance, double capacitance, double edgeCapacitance) {
        resistanceSetting = makeParasiticSetting("Resistance", resistance);
        capacitanceSetting = makeParasiticSetting("Capacitance", capacitance);
        edgeCapacitanceSetting = makeParasiticSetting("EdgeCapacitance", edgeCapacitance);
    }

//    /**
//     * Reset this layer's Parasitics to their factory default values
//     */
//    public void resetToFactoryParasitics()
//    {
//        double res = resistanceSetting.getDoubleFactoryValue();
//        double cap = capacitanceSetting.getDoubleFactoryValue();
//        double edgecap = edgeCapacitanceSetting.getDoubleFactoryValue();
//        setResistance(res);
//        setCapacitance(cap);
//        setEdgeCapacitance(edgecap);
//    }
    /**
     * Method to return the resistance for this layer.
     * @return the resistance for this layer.
     */
    public double getResistance() {
        return resistanceSetting.getDouble();
    }

    /**
     * Returns project preferences to tell the resistance for this Layer.
     * @return project preferences to tell the resistance for this Layer.
     */
    public Setting getResistanceSetting() {
        return resistanceSetting;
    }

    /**
     * Method to return the capacitance for this layer.
     * @return the capacitance for this layer.
     */
    public double getCapacitance() {
        return capacitanceSetting.getDouble();
    }

    /**
     * Returns project preferences to tell the capacitance for this Layer.
     * Returns project preferences to tell the capacitance for this Layer.
     */
    public Setting getCapacitanceSetting() {
        return capacitanceSetting;
    }

    /**
     * Method to return the edge capacitance for this layer.
     * @return the edge capacitance for this layer.
     */
    public double getEdgeCapacitance() {
        return edgeCapacitanceSetting.getDouble();
    }

    /**
     * Returns project preferences to tell the edge capacitance for this Layer.
     * Returns project preferences to tell the edge capacitance for this Layer.
     */
    public Setting getEdgeCapacitanceSetting() {
        return edgeCapacitanceSetting;
    }

    /**
     * Method to finish initialization of this Layer.
     */
    void finish() {
        if (resistanceSetting == null || capacitanceSetting == null || edgeCapacitanceSetting == null) {
            setFactoryParasitics(0, 0, 0);
        }
        if (cifLayerSetting == null) {
            setFactoryCIFLayer("");
        }
        if (dxfLayerSetting == null) {
            setFactoryDXFLayer("");
        }
        if (skillLayerSetting == null) {
            setFactorySkillLayer("");
        }
        if (layer3DThicknessSetting == null || layer3DDistanceSetting == null) {
            double thickness = layer3DThicknessSetting != null ? getThickness() : DEFAULT_THICKNESS;
            double distance = layer3DDistanceSetting != null ? getDistance() : DEFAULT_DISTANCE;
            setFactory3DInfo(thickness, distance);
        }
    }

    /**
     * Returns a printable version of this Layer.
     * @return a printable version of this Layer.
     */
    public String toString() {
        return "Layer " + getName();
    }

    public void copyState(Layer that) {
        assert getName().equals(that.getName());
        if (pureLayerNode != null) {
            assert pureLayerNode.getId() == that.pureLayerNode.getId();
//            pureLayerNode.setDefSize(that.pureLayerNode.getDefWidth(), that.pureLayerNode.getDefHeight());
        }
    }

    void dump(PrintWriter out, Map<Setting, Object> settings) {
        final String[] layerBits = {
            null, null, null,
            null, null, null,
            "PTYPE", "NTYPE", "DEPLETION",
            "ENHANCEMENT", "LIGHT", "HEAVY",
            null, "NONELEC", "CONMETAL",
            "CONPOLY", "CONDIFF", null,
            null, null, null,
            "HLVT", "INTRANS", "THICK"
        };
        out.print("Layer " + getName() + " " + getFunction().name());
        Technology.printlnBits(out, layerBits, getFunctionExtras());
        out.print("\t");
        Technology.printlnSetting(out, settings, getCIFLayerSetting());
        out.print("\t");
        Technology.printlnSetting(out, settings, getDXFLayerSetting());
        out.print("\t");
        Technology.printlnSetting(out, settings, getSkillLayerSetting());
        out.print("\t");
        Technology.printlnSetting(out, settings, getResistanceSetting());
        out.print("\t");
        Technology.printlnSetting(out, settings, getCapacitanceSetting());
        out.print("\t");
        Technology.printlnSetting(out, settings, getEdgeCapacitanceSetting());
        // GDS
        EGraphics factoryDesc = getFactoryGraphics();
        EGraphics desc = factoryDesc;
        out.println("\tpatternedOnDisplay=" + desc.isPatternedOnDisplay() + "(" + factoryDesc.isPatternedOnDisplay() + ")");
        out.println("\tpatternedOnPrinter=" + desc.isPatternedOnPrinter() + "(" + factoryDesc.isPatternedOnPrinter() + ")");
        out.println("\toutlined=" + desc.getOutlined() + "(" + factoryDesc.getOutlined() + ")");
        out.println("\ttransparent=" + desc.getTransparentLayer() + "(" + factoryDesc.getTransparentLayer() + ")");
        out.println("\tcolor=" + Integer.toHexString(desc.getColor().getRGB()) + "(" + Integer.toHexString(factoryDesc.getRGB()) + ")");
        out.println("\topacity=" + desc.getOpacity() + "(" + factoryDesc.getOpacity() + ")");
        out.println("\tforeground=" + factoryDesc.getForeground());
        int pattern[] = factoryDesc.getPattern();
        out.print("\tpattern");
        for (int p : pattern) {
            out.print(" " + Integer.toHexString(p));
        }
        out.println();
        out.println("\tdistance3D=" + getDistanceSetting().getDoubleFactoryValue());
        out.println("\tthickness3D=" + getThicknessSetting().getDoubleFactoryValue());
        out.println("\tmode3D=" + factoryDesc.getTransparencyMode());
        out.println("\tfactor3D=" + factoryDesc.getTransparencyFactor());
    }

    /**
     * Method to create XML version of a Layer.
     * @return the Xml.Layer for the Layer.
     */
    Xml.Layer makeXml() {
        Xml.Layer l = new Xml.Layer();
        l.name = getName();
        l.function = getFunction();
        l.extraFunction = getFunctionExtras();
        l.desc = getFactoryGraphics();
        l.height3D = getDistanceSetting().getDoubleFactoryValue();
        l.thick3D = getThicknessSetting().getDoubleFactoryValue();
        l.cif = (String) getCIFLayerSetting().getFactoryValue();
        l.skill = (String) getSkillLayerSetting().getFactoryValue();
        l.resistance = getResistanceSetting().getDoubleFactoryValue();
        l.capacitance = getCapacitanceSetting().getDoubleFactoryValue();
        l.edgeCapacitance = getEdgeCapacitanceSetting().getDoubleFactoryValue();
        if (pureLayerNode != null) {
            l.pureLayerNode = new Xml.PureLayerNode();
            l.pureLayerNode.name = pureLayerNode.getName();
            for (Map.Entry<String, PrimitiveNode> e : tech.getOldNodeNames().entrySet()) {
                if (e.getValue() != pureLayerNode) {
                    continue;
                }
                assert l.pureLayerNode.oldName == null;
                l.pureLayerNode.oldName = e.getKey();
            }
            l.pureLayerNode.style = pureLayerNode.getNodeLayers()[0].getStyle();
            l.pureLayerNode.port = pureLayerNode.getPort(0).getName();
            l.pureLayerNode.size.addLambda(pureLayerNode.getFactoryDefaultSize().getLambdaX());
            for (ArcProto ap : pureLayerNode.getPort(0).getConnections()) {
                if (ap.getTechnology() != tech) {
                    continue;
                }
                l.pureLayerNode.portArcs.add(ap.getName());
            }
        }
        return l;
    }
}
