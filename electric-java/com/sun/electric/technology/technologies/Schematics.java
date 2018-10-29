/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Schematics.java
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
package com.sun.electric.technology.technologies;

import static com.sun.electric.util.collections.ArrayIterator.i2i;

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.PortProtoId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.BoundsBuilder;
import com.sun.electric.technology.EdgeH;
import com.sun.electric.technology.EdgeV;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitiveNodeSize;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.TransistorSize;
import com.sun.electric.technology.Xml;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.user.User;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.MutableInteger;

import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This is the Schematics technology.
 */
public class Schematics extends Technology {

    /** key of Variable holding global signal name. */
    public static final Variable.Key SCHEM_GLOBAL_NAME = Variable.newKey("SCHEM_global_name");
    /** key of Variable holding resistance. */
    public static final Variable.Key SCHEM_RESISTANCE = Variable.newKey("SCHEM_resistance");
    /** key of Variable holding capacitance. */
    public static final Variable.Key SCHEM_CAPACITANCE = Variable.newKey("SCHEM_capacitance");
    /** key of Variable holding inductance. */
    public static final Variable.Key SCHEM_INDUCTANCE = Variable.newKey("SCHEM_inductance");
    /** key of Variable holding diode area. */
    public static final Variable.Key SCHEM_DIODE = Variable.newKey("SCHEM_diode");
    /** key of Variable holding black-box function. */
    public static final Variable.Key SCHEM_FUNCTION = Variable.newKey("SCHEM_function");
    /** key of Variable holding transistor width. */
    public static final Variable.Key ATTR_WIDTH = Variable.newKey("ATTR_width");
    /** key of Variable holding transistor length. */
    public static final Variable.Key ATTR_LENGTH = Variable.newKey("ATTR_length");
    /** key of Variable holding transistor area. */
    public static final Variable.Key ATTR_AREA = Variable.newKey("ATTR_area");

    /** the Schematics Technology object. */
    public static Schematics tech() {
        return TechPool.getThreadTechPool().getSchematics();
    }
//	/** Defines the Flip-flop type. */						private static final int FFTYPE =    07;
    /** Defines an RS Flip-flop. */
    private static final int FFTYPERS = 0;
    /** Defines a JK Flip-flop. */
    private static final int FFTYPEJK = 1;
    /** Defines a D Flip-flop. */
    private static final int FFTYPED = 2;
    /** Defines a T Flip-flop. */
    private static final int FFTYPET = 3;
//	/** Defines the Flip-flop clocking bits. */				private static final int FFCLOCK =  014;
    /** Defines a Master/Slave Flip-flop. */
    private static final int FFCLOCKMS = 0;
    /** Defines a Positive clock Flip-flop. */
    private static final int FFCLOCKP = 04;
    /** Defines a Negative clock Flip-flop. */
    private static final int FFCLOCKN = 010;
    /** Defines an nMOS transistor. */
    private static final int TRANNMOS = 0;
    /** Defines a nMOS depletion transistor. */
    private static final int TRANNMOSD = 1;
    /** Defines a pMOS transistor. */
    private static final int TRANPMOS = 2;
    /** Defines an NPN Junction transistor. */
    private static final int TRANNPN = 3;
    /** Defines a PNP Junction transistor. */
    private static final int TRANPNP = 4;
    /** Defines an N Junction FET transistor. */
    private static final int TRANNJFET = 5;
    /** Defines a P Junction FET transistor. */
    private static final int TRANPJFET = 6;
    /** Defines a Depletion MESFET transistor. */
    private static final int TRANDMES = 7;
    /** Defines an Enhancement MESFET transistor. */
    private static final int TRANEMES = 8;
    /** Defines a pMOS depletion transistor. */
    private static final int TRANPMOSD = 9;
    /** Defines a nMOS native transistor. */
    private static final int TRANNMOSNT = 10;
    /** Defines a pMOS native transistor. */
    private static final int TRANPMOSNT = 11;
    /** Defines a nMOS floating gate transistor. */
    private static final int TRANNMOSFG = 12;
    /** Defines a pMOS floating gate transistor. */
    private static final int TRANPMOSFG = 13;
    /** Defines a nMOS low threshold transistor. */
    private static final int TRANNMOSVTL = 14;
    /** Defines a pMOS low threshold transistor. */
    private static final int TRANPMOSVTL = 15;
    /** Defines a nMOS high threshold transistor. */
    private static final int TRANNMOSVTH = 16;
    /** Defines a pMOS high threshold transistor. */
    private static final int TRANPMOSVTH = 17;
    /** Defines a nMOS high voltage-1 transistor. */
    private static final int TRANNMOSHV1 = 18;
    /** Defines a pMOS high voltage-1 transistor. */
    private static final int TRANPMOSHV1 = 19;
    /** Defines a nMOS high voltage-2 transistor. */
    private static final int TRANNMOSHV2 = 20;
    /** Defines a pMOS high voltage-2 transistor. */
    private static final int TRANPMOSHV2 = 21;
    /** Defines a nMOS high voltage-3 transistor. */
    private static final int TRANNMOSHV3 = 22;
    /** Defines a pMOS high voltage-3 transistor. */
    private static final int TRANPMOSHV3 = 23;
    /** Defines a nMOS native high voltage-1 transistor. */
    private static final int TRANNMOSNTHV1 = 24;
    /** Defines a pMOS native high voltage-1 transistor. */
    private static final int TRANPMOSNTHV1 = 25;
    /** Defines a nMOS native high voltage-2 transistor. */
    private static final int TRANNMOSNTHV2 = 26;
    /** Defines a pMOS native high voltage-2 transistor. */
    private static final int TRANPMOSNTHV2 = 27;
    /** Defines a nMOS native high voltage-3 transistor. */
    private static final int TRANNMOSNTHV3 = 28;
    /** Defines a pMOS native high voltage-3 transistor. */
    private static final int TRANPMOSNTHV3 = 29;
    /** Defines a NMOS Carbon Nanotube transistor. */
    private static final int TRANNMOSCN = 30;
    /** Defines a PMOS Carbon Nanotube transistor. */
    private static final int TRANPMOSCN = 31;
    /** Defines a normal Diode. */
    private static final int DIODENORM = 0;
    /** Defines a Zener Diode. */
    private static final int DIODEZENER = 1;
    /** Defines a normal Capacitor. */
    private static final int CAPACNORM = 0;
    /** Defines an Electrolytic Capacitor. */
    private static final int CAPACELEC = 1;
    /** Defines a Poly2 Capacitor. */
    private static final int CAPACPOLY2 = 2;
    /** Defines a normal Resistor. */
    private static final int RESISTNORM = 0;
    /** Defines an n-poly Resistor. */
    private static final int RESISTNPOLY = 1;
    /** Defines a p-poly Resistor. */
    private static final int RESISTPPOLY = 2;
    /** Defines an n-well Resistor. */
    private static final int RESISTNWELL = 3;
    /** Defines a p-well Resistor. */
    private static final int RESISTPWELL = 4;
    /** Defines a n-active Resistor. */
    private static final int RESISTNACTIVE = 5;
    /** Defines a p-active Resistor. */
    private static final int RESISTPACTIVE = 6;
    /** Defines an n-poly non silicide Resistor. */
    private static final int RESISTNNSPOLY = 7;
    /** Defines a p-poly non silicide Resistor. */
    private static final int RESISTPNSPOLY = 8;
    /** Defines a hi resisistant poly2 Resistor. */
    private static final int RESISTHIRESPOLY2 = 9;
    /** Defines a Transconductance two-port (VCCS). */
    private static final int TWOPVCCS = 0;
    /** Defines a Transresistance two-port (CCVS). */
    private static final int TWOPCCVS = 1;
    /** Defines a Voltage gain two-port (VCVS). */
    private static final int TWOPVCVS = 2;
    /** Defines a Current gain two-port (CCCS). */
    private static final int TWOPCCCS = 3;
    /** Defines a Transmission Line two-port. */
    private static final int TWOPTLINE = 4;
    /** the node layer */
    public final Layer node_lay;
    /** wire arc */
    public final ArcProto wire_arc;
    /** bus arc */
    public final ArcProto bus_arc;
    /** wire-pin */
    public final PrimitiveNode wirePinNode;
    /** bus-pin */
    public final PrimitiveNode busPinNode;
    /** wire-con */
    public final PrimitiveNode wireConNode;
    /** buffer */
    public final PrimitiveNode bufferNode;
    /** and */
    public final PrimitiveNode andNode;
    /** or */
    public final PrimitiveNode orNode;
    /** xor */
    public final PrimitiveNode xorNode;
    /** flipflop */
    public final PrimitiveNode flipflopNode;
    /** mux */
    public final PrimitiveNode muxNode;
    /** bbox */
    public final PrimitiveNode bboxNode;
    /** switch */
    public final PrimitiveNode switchNode;
    /** offpage */
    public final PrimitiveNode offpageNode;
    /** power */
    public final PrimitiveNode powerNode;
    /** ground */
    public final PrimitiveNode groundNode;
    /** source */
    public final PrimitiveNode sourceNode;
    /** transistor */
    public final PrimitiveNode transistorNode;
    /** resistor */
    public final PrimitiveNode resistorNode;
    /** capacitor */
    public final PrimitiveNode capacitorNode;
    /** diode */
    public final PrimitiveNode diodeNode;
    /** inductor */
    public final PrimitiveNode inductorNode;
    /** meter */
    public final PrimitiveNode meterNode;
    /** well */
    public final PrimitiveNode wellNode;
    /** substrate */
    public final PrimitiveNode substrateNode;
    /** twoport */
    public final PrimitiveNode twoportNode;
    /** transistor-4 */
    public final PrimitiveNode transistor4Node;
    /** global */
    public final PrimitiveNode globalNode;
    /** global partition */
    public final PrimitiveNode globalPartitionNode;
    // Tech params
    private Double paramNegatingBubbleSize;
    private final Layer arc_lay, bus_lay, text_lay;
    private final Xml.Technology xmlSch;
    // this much from the center to the left edge
	/* 0.1 */ private final EdgeH LEFTBYP1 = EdgeH.by0(-0.1);
    /* 0.1333... */ private final EdgeH LEFTBYP125 = EdgeH.by0(-0.125);
    /* 0.2 */ private final EdgeH LEFTBYP2 = EdgeH.by0(-0.2);
    /* 0.25 */ private final EdgeH LEFTBYP25 = EdgeH.by0(-0.25);
    /* 0.3 */ private final EdgeH LEFTBYP3 = EdgeH.by0(-0.3);
    /* 0.35 */ private final EdgeH LEFTBYP35 = EdgeH.by0(-0.35);
    /* 0.6166... */ private final EdgeH LEFTBYP375 = EdgeH.by0(-0.375);
    /* 0.4 */ private final EdgeH LEFTBYP4 = EdgeH.by0(-0.4);
    /* 0.45 */ private final EdgeH LEFTBYP45 = EdgeH.by0(-0.45);
    /* 0.5 */ private final EdgeH LEFTBYP5 = EdgeH.by0(-0.5);
    /* 0.6 */ private final EdgeH LEFTBYP6 = EdgeH.by0(-0.6);
    /* 0.6166... */ private final EdgeH LEFTBYP625 = EdgeH.by0(-0.625);
    /* 0.6666... */ private final EdgeH LEFTBYP66 = EdgeH.by0(-0.6666666666);
    /* 0.7 */ private final EdgeH LEFTBYP7 = EdgeH.by0(-0.7);
    /* 0.75 */ private final EdgeH LEFTBYP75 = EdgeH.by0(-0.75);
    /* 0.8 */ private final EdgeH LEFTBYP8 = EdgeH.by0(-0.8);
    /* 0.875 */ private final EdgeH LEFTBYP875 = EdgeH.by0(-0.875);
    /* 0.9 */ private final EdgeH LEFTBYP9 = EdgeH.by0(-0.9);
//	/* 1.2 */			private final EdgeH LEFTBYP12 = EdgeH.by0(-1.2);
//	/* 1.4 */			private final EdgeH LEFTBYP14 = EdgeH.by0(-1.4);
	/* 1.6 */ private final EdgeH LEFTBY1P6 = EdgeH.by0(-1.6);

    /* 0.5 */ private final EdgeH LEFT2BYP5 = EdgeH.by2(-0.5);
    /* 0.1 */ private final EdgeH LEFT3BYP1 = EdgeH.by3(-0.1);
    /* 0.1333... */ private final EdgeH LEFT3BYP125 = EdgeH.by3(-0.125);
    /* 0.2 */ private final EdgeH LEFT3BYP2 = EdgeH.by3(-0.2);
    /* 0.25 */ private final EdgeH LEFT3BYP25 = EdgeH.by3(-0.25);
    /* 0.3 */ private final EdgeH LEFT3BYP3 = EdgeH.by3(-0.3);
    /* 0.35 */ private final EdgeH LEFT3BYP35 = EdgeH.by3(-0.35);
    /* 0.6166... */ private final EdgeH LEFT3BYP375 = EdgeH.by3(-0.375);
    /* 0.4 */ private final EdgeH LEFT3BYP4 = EdgeH.by3(-0.4);
    /* 0.45 */ private final EdgeH LEFT3BYP45 = EdgeH.by3(-0.45);
    /* 0.5 */ private final EdgeH LEFT3BYP5 = EdgeH.by3(-0.5);
    /* 0.6 */ private final EdgeH LEFT3BYP6 = EdgeH.by3(-0.6);
    /* 0.6166... */ private final EdgeH LEFT3BYP625 = EdgeH.by3(-0.625);
    /* 0.6666... */ private final EdgeH LEFT3BYP66 = EdgeH.by3(-0.6666666666);
    /* 0.7 */ private final EdgeH LEFT3BYP7 = EdgeH.by3(-0.7);
    /* 0.75 */ private final EdgeH LEFT3BYP75 = EdgeH.by3(-0.75);
    /* 0.8 */ private final EdgeH LEFT3BYP8 = EdgeH.by3(-0.8);
    /* 0.875 */ private final EdgeH LEFT3BYP875 = EdgeH.by3(-0.875);
    /* 0.9 */ private final EdgeH LEFT3BYP9 = EdgeH.by3(-0.9);
//	/* 1.2 */			private final EdgeH LEFT3BYP12 = EdgeH.by3(-1.2);
//	/* 1.4 */			private final EdgeH LEFT3BYP14 = EdgeH.by3(-1.4);
	/* 1.6 */ private final EdgeH LEFT3BY1P6 = EdgeH.by3(-1.6);

    /* 0.1 */ private final EdgeH LEFT4BYP1 = EdgeH.by4(-0.1);
    /* 0.1333... */ private final EdgeH LEFT4BYP125 = EdgeH.by4(-0.125);
    /* 0.2 */ private final EdgeH LEFT4BYP2 = EdgeH.by4(-0.2);
    /* 0.25 */ private final EdgeH LEFT4BYP25 = EdgeH.by4(-0.25);
    /* 0.3 */ private final EdgeH LEFT4BYP3 = EdgeH.by4(-0.3);
    /* 0.35 */ private final EdgeH LEFT4BYP35 = EdgeH.by4(-0.35);
    /* 0.6166... */ private final EdgeH LEFT4BYP375 = EdgeH.by4(-0.375);
    /* 0.4 */ private final EdgeH LEFT4BYP4 = EdgeH.by4(-0.4);
    /* 0.45 */ private final EdgeH LEFT4BYP45 = EdgeH.by4(-0.45);
    /* 0.5 */ private final EdgeH LEFT4BYP5 = EdgeH.by4(-0.5);
    /* 0.6 */ private final EdgeH LEFT4BYP6 = EdgeH.by4(-0.6);
    /* 0.6166... */ private final EdgeH LEFT4BYP625 = EdgeH.by4(-0.625);
    /* 0.6666... */ private final EdgeH LEFT4BYP66 = EdgeH.by4(-0.6666666666);
    /* 0.7 */ private final EdgeH LEFT4BYP7 = EdgeH.by4(-0.7);
    /* 0.75 */ private final EdgeH LEFT4BYP75 = EdgeH.by4(-0.75);
    /* 0.8 */ private final EdgeH LEFT4BYP8 = EdgeH.by4(-0.8);
    /* 0.875 */ private final EdgeH LEFT4BYP875 = EdgeH.by4(-0.875);
    /* 0.9 */ private final EdgeH LEFT4BYP9 = EdgeH.by4(-0.9);
//	/* 1.2 */			private final EdgeH LEFT4BYP12 = EdgeH.by4(-1.2);
//	/* 1.4 */			private final EdgeH LEFT4BYP14 = EdgeH.by4(-1.4);
	/* 1.6 */ private final EdgeH LEFT4BY1P6 = EdgeH.by4(-1.6);

    /* 0.1 */ private final EdgeH LEFT6BYP1 = EdgeH.by6(-0.1);
    /* 0.1333... */ private final EdgeH LEFT6BYP125 = EdgeH.by6(-0.125);
    /* 0.2 */ private final EdgeH LEFT6BYP2 = EdgeH.by6(-0.2);
    /* 0.25 */ private final EdgeH LEFT6BYP25 = EdgeH.by6(-0.25);
    /* 0.3 */ private final EdgeH LEFT6BYP3 = EdgeH.by6(-0.3);
    /* 0.35 */ private final EdgeH LEFT6BYP35 = EdgeH.by6(-0.35);
    /* 0.6166... */ private final EdgeH LEFT6BYP375 = EdgeH.by6(-0.375);
    /* 0.4 */ private final EdgeH LEFT6BYP4 = EdgeH.by6(-0.4);
    /* 0.45 */ private final EdgeH LEFT6BYP45 = EdgeH.by6(-0.45);
    /* 0.5 */ private final EdgeH LEFT6BYP5 = EdgeH.by6(-0.5);
    /* 0.6 */ private final EdgeH LEFT6BYP6 = EdgeH.by6(-0.6);
    /* 0.6166... */ private final EdgeH LEFT6BYP625 = EdgeH.by6(-0.625);
    /* 0.6666... */ private final EdgeH LEFT6BYP66 = EdgeH.by6(-0.6666666666);
    /* 0.7 */ private final EdgeH LEFT6BYP7 = EdgeH.by6(-0.7);
    /* 0.75 */ private final EdgeH LEFT6BYP75 = EdgeH.by6(-0.75);
    /* 0.8 */ private final EdgeH LEFT6BYP8 = EdgeH.by6(-0.8);
    /* 0.875 */ private final EdgeH LEFT6BYP875 = EdgeH.by6(-0.875);
    /* 0.9 */ private final EdgeH LEFT6BYP9 = EdgeH.by6(-0.9);
//	/* 1.2 */			private final EdgeH LEFT6BYP12 = EdgeH.by6(-1.2);
//	/* 1.4 */			private final EdgeH LEFT6BYP14 = EdgeH.by6(-1.4);
	/* 1.6 */ private final EdgeH LEFT6BY1P6 = EdgeH.by6(-1.6);

    /* 0.8 */ private final EdgeH LEFT8BYP8 = EdgeH.by8(-0.8);
    /* 0.1 */ private final EdgeH LEFT10BYP1 = EdgeH.by10(-0.1);
    /* 0.1333... */ private final EdgeH LEFT10BYP125 = EdgeH.by10(-0.125);
    /* 0.2 */ private final EdgeH LEFT10BYP2 = EdgeH.by10(-0.2);
    /* 0.25 */ private final EdgeH LEFT10BYP25 = EdgeH.by10(-0.25);
    /* 0.3 */ private final EdgeH LEFT10BYP3 = EdgeH.by10(-0.3);
    /* 0.35 */ private final EdgeH LEFT10BYP35 = EdgeH.by10(-0.35);
    /* 0.6166... */ private final EdgeH LEFT10BYP375 = EdgeH.by10(-0.375);
    /* 0.4 */ private final EdgeH LEFT10BYP4 = EdgeH.by10(-0.4);
    /* 0.45 */ private final EdgeH LEFT10BYP45 = EdgeH.by10(-0.45);
    /* 0.5 */ private final EdgeH LEFT10BYP5 = EdgeH.by10(-0.5);
    /* 0.6 */ private final EdgeH LEFT10BYP6 = EdgeH.by10(-0.6);
    /* 0.6166... */ private final EdgeH LEFT10BYP625 = EdgeH.by10(-0.625);
    /* 0.6666... */ private final EdgeH LEFT10BYP66 = EdgeH.by10(-0.6666666666);
    /* 0.7 */ private final EdgeH LEFT10BYP7 = EdgeH.by10(-0.7);
    /* 0.75 */ private final EdgeH LEFT10BYP75 = EdgeH.by10(-0.75);
    /* 0.8 */ private final EdgeH LEFT10BYP8 = EdgeH.by10(-0.8);
    /* 0.875 */ private final EdgeH LEFT10BYP875 = EdgeH.by10(-0.875);
    /* 0.9 */ private final EdgeH LEFT10BYP9 = EdgeH.by10(-0.9);
//	/* 1.2 */			private final EdgeH LEFT10BYP12 = EdgeH.by10(-1.2);
//	/* 1.4 */			private final EdgeH LEFT10BYP14 = EdgeH.by10(-1.4);
	/* 1.6 */ private final EdgeH LEFT10BY1P6 = EdgeH.by10(-1.6);
    // this much from the center to the right edge
	/* 0.1 */ private final EdgeH RIGHTBYP1 = EdgeH.by0(0.1);
    /* 0.1333... */ private final EdgeH RIGHTBYP125 = EdgeH.by0(0.125);
    /* 0.2 */ private final EdgeH RIGHTBYP2 = EdgeH.by0(0.2);
    /* 0.25 */ private final EdgeH RIGHTBYP25 = EdgeH.by0(0.25);
    /* 0.3 */ private final EdgeH RIGHTBYP3 = EdgeH.by0(0.3);
    /* 0.3333... */ private final EdgeH RIGHTBYP33 = EdgeH.by0(0.3333333333);
    /* 0.35 */ private final EdgeH RIGHTBYP35 = EdgeH.by0(0.35);
    /* 0.6166... */ private final EdgeH RIGHTBYP375 = EdgeH.by0(0.375);
    /* 0.3833... */ private final EdgeH RIGHTBYP3833 = EdgeH.by0(0.3833333333);
    /* 0.4 */ private final EdgeH RIGHTBYP4 = EdgeH.by0(0.4);
    /* 0.4333... */ private final EdgeH RIGHTBYP433 = EdgeH.by0(0.4333333333);
    /* 0.45 */ private final EdgeH RIGHTBYP45 = EdgeH.by0(0.45);
    /* 0.5 */ private final EdgeH RIGHTBYP5 = EdgeH.by0(0.5);
    /* 0.5166... */ private final EdgeH RIGHTBYP5166 = EdgeH.by0(0.5166666666);
//	/* 0.55 */			private final EdgeH RIGHTBYP55 = EdgeH.by0(0.55);
	/* 0.5666... */ private final EdgeH RIGHTBYP566 = EdgeH.by0(0.5666666666);
    /* 0.6 */ private final EdgeH RIGHTBYP6 = EdgeH.by0(0.6);
    /* 0.6166... */ private final EdgeH RIGHTBYP6166 = EdgeH.by0(0.6166666666);
    /* 0.6166... */ private final EdgeH RIGHTBYP625 = EdgeH.by0(0.625);
    /* 0.6666... */ private final EdgeH RIGHTBYP66 = EdgeH.by0(0.6666666666);
//	/* 0.7 */			private final EdgeH RIGHTBYP7 = EdgeH.by0(0.7);
	/* 0.75 */ private final EdgeH RIGHTBYP75 = EdgeH.by0(0.75);
    /* 0.8 */ private final EdgeH RIGHTBYP8 = EdgeH.by0(0.8);
    /* 0.875 */ private final EdgeH RIGHTBYP875 = EdgeH.by0(0.875);
    /* 0.9 */ private final EdgeH RIGHTBYP9 = EdgeH.by0(0.9);

    /* 0.1 */ private final EdgeH RIGHT3BYP1 = EdgeH.by3(0.1);
    /* 0.1333... */ private final EdgeH RIGHT3BYP125 = EdgeH.by3(0.125);
    /* 0.2 */ private final EdgeH RIGHT3BYP2 = EdgeH.by3(0.2);
    /* 0.25 */ private final EdgeH RIGHT3BYP25 = EdgeH.by3(0.25);
    /* 0.3 */ private final EdgeH RIGHT3BYP3 = EdgeH.by3(0.3);
    /* 0.3333... */ private final EdgeH RIGHT3BYP33 = EdgeH.by3(0.3333333333);
    /* 0.35 */ private final EdgeH RIGHT3BYP35 = EdgeH.by3(0.35);
    /* 0.6166... */ private final EdgeH RIGHT3BYP375 = EdgeH.by3(0.375);
    /* 0.3833... */ private final EdgeH RIGHT3BYP3833 = EdgeH.by3(0.3833333333);
    /* 0.4 */ private final EdgeH RIGHT3BYP4 = EdgeH.by3(0.4);
    /* 0.4333... */ private final EdgeH RIGHT3BYP433 = EdgeH.by3(0.4333333333);
    /* 0.45 */ private final EdgeH RIGHT3BYP45 = EdgeH.by3(0.45);
    /* 0.5 */ private final EdgeH RIGHT3BYP5 = EdgeH.by3(0.5);
    /* 0.5166... */ private final EdgeH RIGHT3BYP5166 = EdgeH.by3(0.5166666666);
//	/* 0.55 */			private final EdgeH RIGHT3BYP55 = EdgeH.by3(0.55);
	/* 0.5666... */ private final EdgeH RIGHT3BYP566 = EdgeH.by3(0.5666666666);
    /* 0.6 */ private final EdgeH RIGHT3BYP6 = EdgeH.by3(0.6);
    /* 0.6166... */ private final EdgeH RIGHT3BYP6166 = EdgeH.by3(0.6166666666);
    /* 0.6166... */ private final EdgeH RIGHT3BYP625 = EdgeH.by3(0.625);
    /* 0.6666... */ private final EdgeH RIGHT3BYP66 = EdgeH.by3(0.6666666666);
//	/* 0.7 */			private final EdgeH RIGHT3BYP7 = EdgeH.by3(0.7);
	/* 0.75 */ private final EdgeH RIGHT3BYP75 = EdgeH.by3(0.75);
    /* 0.8 */ private final EdgeH RIGHT3BYP8 = EdgeH.by3(0.8);
    /* 0.875 */ private final EdgeH RIGHT3BYP875 = EdgeH.by3(0.875);
    /* 0.9 */ private final EdgeH RIGHT3BYP9 = EdgeH.by3(0.9);

    /* 0.1 */ private final EdgeH RIGHT4BYP1 = EdgeH.by4(0.1);
    /* 0.1333... */ private final EdgeH RIGHT4BYP125 = EdgeH.by4(0.125);
    /* 0.2 */ private final EdgeH RIGHT4BYP2 = EdgeH.by4(0.2);
    /* 0.25 */ private final EdgeH RIGHT4BYP25 = EdgeH.by4(0.25);
    /* 0.3 */ private final EdgeH RIGHT4BYP3 = EdgeH.by4(0.3);
    /* 0.3333... */ private final EdgeH RIGHT4BYP33 = EdgeH.by4(0.3333333333);
    /* 0.35 */ private final EdgeH RIGHT4BYP35 = EdgeH.by4(0.35);
    /* 0.6166... */ private final EdgeH RIGHT4BYP375 = EdgeH.by4(0.375);
    /* 0.3833... */ private final EdgeH RIGHT4BYP3833 = EdgeH.by4(0.3833333333);
    /* 0.4 */ private final EdgeH RIGHT4BYP4 = EdgeH.by4(0.4);
    /* 0.4333... */ private final EdgeH RIGHT4BYP433 = EdgeH.by4(0.4333333333);
    /* 0.45 */ private final EdgeH RIGHT4BYP45 = EdgeH.by4(0.45);
    /* 0.5 */ private final EdgeH RIGHT4BYP5 = EdgeH.by4(0.5);
    /* 0.5166... */ private final EdgeH RIGHT4BYP5166 = EdgeH.by4(0.5166666666);
//	/* 0.55 */			private final EdgeH RIGHT4BYP55 = EdgeH.by4(0.55);
	/* 0.5666... */ private final EdgeH RIGHT4BYP566 = EdgeH.by4(0.5666666666);
    /* 0.6 */ private final EdgeH RIGHT4BYP6 = EdgeH.by4(0.6);
    /* 0.6166... */ private final EdgeH RIGHT4BYP6166 = EdgeH.by4(0.6166666666);
    /* 0.6166... */ private final EdgeH RIGHT4BYP625 = EdgeH.by4(0.625);
    /* 0.6666... */ private final EdgeH RIGHT4BYP66 = EdgeH.by4(0.6666666666);
//	/* 0.7 */			private final EdgeH RIGHT4BYP7 = EdgeH.by4(0.7);
	/* 0.75 */ private final EdgeH RIGHT4BYP75 = EdgeH.by4(0.75);
    /* 0.8 */ private final EdgeH RIGHT4BYP8 = EdgeH.by4(0.8);
    /* 0.875 */ private final EdgeH RIGHT4BYP875 = EdgeH.by4(0.875);
    /* 0.9 */ private final EdgeH RIGHT4BYP9 = EdgeH.by4(0.9);

    /* 0.1 */ private final EdgeH RIGHT6BYP1 = EdgeH.by6(0.1);
    /* 0.1333... */ private final EdgeH RIGHT6BYP125 = EdgeH.by6(0.125);
    /* 0.2 */ private final EdgeH RIGHT6BYP2 = EdgeH.by6(0.2);
    /* 0.25 */ private final EdgeH RIGHT6BYP25 = EdgeH.by6(0.25);
    /* 0.3 */ private final EdgeH RIGHT6BYP3 = EdgeH.by6(0.3);
    /* 0.3333... */ private final EdgeH RIGHT6BYP33 = EdgeH.by6(0.3333333333);
    /* 0.35 */ private final EdgeH RIGHT6BYP35 = EdgeH.by6(0.35);
    /* 0.6166... */ private final EdgeH RIGHT6BYP375 = EdgeH.by6(0.375);
    /* 0.3833... */ private final EdgeH RIGHT6BYP3833 = EdgeH.by6(0.3833333333);
    /* 0.4 */ private final EdgeH RIGHT6BYP4 = EdgeH.by6(0.4);
    /* 0.4333... */ private final EdgeH RIGHT6BYP433 = EdgeH.by6(0.4333333333);
    /* 0.45 */ private final EdgeH RIGHT6BYP45 = EdgeH.by6(0.45);
    /* 0.5 */ private final EdgeH RIGHT6BYP5 = EdgeH.by6(0.5);
    /* 0.5166... */ private final EdgeH RIGHT6BYP5166 = EdgeH.by6(0.5166666666);
//	/* 0.55 */			private final EdgeH RIGHT6BYP55 = EdgeH.by6(0.55);
	/* 0.5666... */ private final EdgeH RIGHT6BYP566 = EdgeH.by6(0.5666666666);
    /* 0.6 */ private final EdgeH RIGHT6BYP6 = EdgeH.by6(0.6);
    /* 0.6166... */ private final EdgeH RIGHT6BYP6166 = EdgeH.by6(0.6166666666);
    /* 0.6166... */ private final EdgeH RIGHT6BYP625 = EdgeH.by6(0.625);
    /* 0.6666... */ private final EdgeH RIGHT6BYP66 = EdgeH.by6(0.6666666666);
//	/* 0.7 */			private final EdgeH RIGHT6BYP7 = EdgeH.by6(0.7);
	/* 0.75 */ private final EdgeH RIGHT6BYP75 = EdgeH.by6(0.75);
    /* 0.8 */ private final EdgeH RIGHT6BYP8 = EdgeH.by6(0.8);
    /* 0.875 */ private final EdgeH RIGHT6BYP875 = EdgeH.by6(0.875);
    /* 0.9 */ private final EdgeH RIGHT6BYP9 = EdgeH.by6(0.9);

    /* 0.8 */ private final EdgeH RIGHT8BYP8 = EdgeH.by8(0.8);
    /* 0.1 */ private final EdgeH RIGHT10BYP1 = EdgeH.by10(0.1);
    /* 0.1333... */ private final EdgeH RIGHT10BYP125 = EdgeH.by10(0.125);
    /* 0.2 */ private final EdgeH RIGHT10BYP2 = EdgeH.by10(0.2);
    /* 0.25 */ private final EdgeH RIGHT10BYP25 = EdgeH.by10(0.25);
    /* 0.3 */ private final EdgeH RIGHT10BYP3 = EdgeH.by10(0.3);
    /* 0.3333... */ private final EdgeH RIGHT10BYP33 = EdgeH.by10(0.3333333333);
    /* 0.35 */ private final EdgeH RIGHT10BYP35 = EdgeH.by10(0.35);
    /* 0.6166... */ private final EdgeH RIGHT10BYP375 = EdgeH.by10(0.375);
    /* 0.3833... */ private final EdgeH RIGHT10BYP3833 = EdgeH.by10(0.3833333333);
    /* 0.4 */ private final EdgeH RIGHT10BYP4 = EdgeH.by10(0.4);
    /* 0.4333... */ private final EdgeH RIGHT10BYP433 = EdgeH.by10(0.4333333333);
    /* 0.45 */ private final EdgeH RIGHT10BYP45 = EdgeH.by10(0.45);
    /* 0.5 */ private final EdgeH RIGHT10BYP5 = EdgeH.by10(0.5);
    /* 0.5166... */ private final EdgeH RIGHT10BYP5166 = EdgeH.by10(0.5166666666);
//	/* 0.55 */			private final EdgeH RIGHT10BYP55 = EdgeH.by10(0.55);
	/* 0.5666... */ private final EdgeH RIGHT10BYP566 = EdgeH.by10(0.5666666666);
    /* 0.6 */ private final EdgeH RIGHT10BYP6 = EdgeH.by10(0.6);
    /* 0.6166... */ private final EdgeH RIGHT10BYP6166 = EdgeH.by10(0.6166666666);
    /* 0.6166... */ private final EdgeH RIGHT10BYP625 = EdgeH.by10(0.625);
    /* 0.6666... */ private final EdgeH RIGHT10BYP66 = EdgeH.by10(0.6666666666);
//	/* 0.7 */			private final EdgeH RIGHT10BYP7 = EdgeH.by10(0.7);
	/* 0.75 */ private final EdgeH RIGHT10BYP75 = EdgeH.by10(0.75);
    /* 0.8 */ private final EdgeH RIGHT10BYP8 = EdgeH.by10(0.8);
    /* 0.875 */ private final EdgeH RIGHT10BYP875 = EdgeH.by10(0.875);
    /* 0.9 */ private final EdgeH RIGHT10BYP9 = EdgeH.by10(0.9);
    // this much from the center to the bottom edge
//	/* 0.1 */			private final EdgeV BOTBYP1 = EdgeV.by0(-0.1);
//	/* 0.125 */			private final EdgeV BOTBYP125 = EdgeV.by0(-0.125);
	/* 0.166...  */ private final EdgeV BOTBYP166 = EdgeV.by0(-0.166666666);
    /* 0.2 */ private final EdgeV BOTBYP2 = EdgeV.by0(-0.2);
    /* 0.25 */ private final EdgeV BOTBYP25 = EdgeV.by0(-0.25);
    /* 0.3 */ private final EdgeV BOTBYP3 = EdgeV.by0(-0.3);
    /* 0.3333... */ private final EdgeV BOTBYP33 = EdgeV.by0(-0.3333333333);
    /* 0.375 */ private final EdgeV BOTBYP375 = EdgeV.by0(-0.375);
    /* 0.4 */ private final EdgeV BOTBYP4 = EdgeV.by0(-0.4);
    /* 0.5 */ private final EdgeV BOTBYP5 = EdgeV.by0(-0.5);
    /* 0.6 */ private final EdgeV BOTBYP6 = EdgeV.by0(-0.6);
    /* 0.6666... */ private final EdgeV BOTBYP66 = EdgeV.by0(-0.6666666666);
//	/* 0.7 */			private final EdgeV BOTBYP7 = EdgeV.by0(-0.7);
	/* 0.75 */ private final EdgeV BOTBYP75 = EdgeV.by0(-0.75);
    /* 0.8 */ private final EdgeV BOTBYP8 = EdgeV.by0(-0.8);
    /* 0.875 */ private final EdgeV BOTBYP875 = EdgeV.by0(-0.875);
    /* 0.9 */ private final EdgeV BOTBYP9 = EdgeV.by0(-0.9);

    /* 0.9 */ private final EdgeV BOT3BYP9 = EdgeV.by3(-0.9);
//	/* 0.1 */			private final EdgeV BOT4BYP1 = EdgeV.by4(-0.1);
//	/* 0.125 */			private final EdgeV BOT4BYP125 = EdgeV.by4(-0.125);
	/* 0.166...  */ private final EdgeV BOT4BYP166 = EdgeV.by4(-0.166666666);
    /* 0.2 */ private final EdgeV BOT4BYP2 = EdgeV.by4(-0.2);
    /* 0.25 */ private final EdgeV BOT4BYP25 = EdgeV.by4(-0.25);
    /* 0.3 */ private final EdgeV BOT4BYP3 = EdgeV.by4(-0.3);
    /* 0.3333... */ private final EdgeV BOT4BYP33 = EdgeV.by4(-0.3333333333);
    /* 0.375 */ private final EdgeV BOT4BYP375 = EdgeV.by4(-0.375);
    /* 0.4 */ private final EdgeV BOT4BYP4 = EdgeV.by4(-0.4);
    /* 0.5 */ private final EdgeV BOT4BYP5 = EdgeV.by4(-0.5);
    /* 0.6 */ private final EdgeV BOT4BYP6 = EdgeV.by4(-0.6);
    /* 0.6666... */ private final EdgeV BOT4BYP66 = EdgeV.by4(-0.6666666666);
//	/* 0.7 */			private final EdgeV BOT4BYP7 = EdgeV.by4(-0.7);
	/* 0.75 */ private final EdgeV BOT4BYP75 = EdgeV.by4(-0.75);
    /* 0.8 */ private final EdgeV BOT4BYP8 = EdgeV.by4(-0.8);
    /* 0.875 */ private final EdgeV BOT4BYP875 = EdgeV.by4(-0.875);
    /* 0.9 */ private final EdgeV BOT4BYP9 = EdgeV.by4(-0.9);
//	/* 0.1 */			private final EdgeV BOT6BYP1 = EdgeV.by6(-0.1);
//	/* 0.125 */			private final EdgeV BOT6BYP125 = EdgeV.by6(-0.125);
	/* 0.166...  */ private final EdgeV BOT6BYP166 = EdgeV.by6(-0.166666666);
    /* 0.2 */ private final EdgeV BOT6BYP2 = EdgeV.by6(-0.2);
    /* 0.25 */ private final EdgeV BOT6BYP25 = EdgeV.by6(-0.25);
    /* 0.3 */ private final EdgeV BOT6BYP3 = EdgeV.by6(-0.3);
    /* 0.3333... */ private final EdgeV BOT6BYP33 = EdgeV.by6(-0.3333333333);
    /* 0.375 */ private final EdgeV BOT6BYP375 = EdgeV.by6(-0.375);
    /* 0.4 */ private final EdgeV BOT6BYP4 = EdgeV.by6(-0.4);
    /* 0.5 */ private final EdgeV BOT6BYP5 = EdgeV.by6(-0.5);
    /* 0.6 */ private final EdgeV BOT6BYP6 = EdgeV.by6(-0.6);
    /* 0.6666... */ private final EdgeV BOT6BYP66 = EdgeV.by6(-0.6666666666);
//	/* 0.7 */			private final EdgeV BOT6BYP7 = EdgeV.by6(-0.7);
	/* 0.75 */ private final EdgeV BOT6BYP75 = EdgeV.by6(-0.75);
    /* 0.8 */ private final EdgeV BOT6BYP8 = EdgeV.by6(-0.8);
    /* 0.875 */ private final EdgeV BOT6BYP875 = EdgeV.by6(-0.875);
    /* 0.9 */ private final EdgeV BOT6BYP9 = EdgeV.by6(-0.9);
//	/* 0.1 */			private final EdgeV BOT10BYP1 = EdgeV.by10(-0.1);
//	/* 0.125 */			private final EdgeV BOT10BYP125 = EdgeV.by10(-0.125);
	/* 0.166...  */ private final EdgeV BOT10BYP166 = EdgeV.by10(-0.166666666);
    /* 0.2 */ private final EdgeV BOT10BYP2 = EdgeV.by10(-0.2);
    /* 0.25 */ private final EdgeV BOT10BYP25 = EdgeV.by10(-0.25);
    /* 0.3 */ private final EdgeV BOT10BYP3 = EdgeV.by10(-0.3);
    /* 0.3333... */ private final EdgeV BOT10BYP33 = EdgeV.by10(-0.3333333333);
    /* 0.375 */ private final EdgeV BOT10BYP375 = EdgeV.by10(-0.375);
    /* 0.4 */ private final EdgeV BOT10BYP4 = EdgeV.by10(-0.4);
    /* 0.5 */ private final EdgeV BOT10BYP5 = EdgeV.by10(-0.5);
    /* 0.6 */ private final EdgeV BOT10BYP6 = EdgeV.by10(-0.6);
    /* 0.6666... */ private final EdgeV BOT10BYP66 = EdgeV.by10(-0.6666666666);
//	/* 0.7 */			private final EdgeV BOT10BYP7 = EdgeV.by10(-0.7);
	/* 0.75 */ private final EdgeV BOT10BYP75 = EdgeV.by10(-0.75);
    /* 0.8 */ private final EdgeV BOT10BYP8 = EdgeV.by10(-0.8);
    /* 0.875 */ private final EdgeV BOT10BYP875 = EdgeV.by10(-0.875);
    /* 0.9 */ private final EdgeV BOT10BYP9 = EdgeV.by10(-0.9);
    // this much from the center to the top edge
//	/* 0.1 */			private final EdgeV TOPBYP1 = EdgeV.by0(0.1);
	/* 0.2 */ private final EdgeV TOPBYP2 = EdgeV.by0(0.2);
    /* 0.25 */ private final EdgeV TOPBYP25 = EdgeV.by0(0.25);
    /* 0.3 */ private final EdgeV TOPBYP3 = EdgeV.by0(0.3);
    /* 0.3333... */ private final EdgeV TOPBYP33 = EdgeV.by0(0.3333333333);
    /* 0.4 */ private final EdgeV TOPBYP4 = EdgeV.by0(0.4);
    /* 0.5 */ private final EdgeV TOPBYP5 = EdgeV.by0(0.5);
    /* 0.5833... */ private final EdgeV TOPBYP5833 = EdgeV.by0(0.5833333333);
    /* 0.6 */ private final EdgeV TOPBYP6 = EdgeV.by0(0.6);
    /* 0.6666... */ private final EdgeV TOPBYP66 = EdgeV.by0(0.6666666666);
    /* 0.75 */ private final EdgeV TOPBYP75 = EdgeV.by0(0.75);
    /* 0.8 */ private final EdgeV TOPBYP8 = EdgeV.by0(0.8);
    /* 0.9 */ private final EdgeV TOPBYP9 = EdgeV.by0(0.9);
    /* 0.75 */ private final EdgeV TOP3BYP75 = EdgeV.by3(0.75);
    /* 0.9 */ private final EdgeV TOP3BYP9 = EdgeV.by3(0.9);
//	/* 0.1 */			private final EdgeV TOP4BYP1 = EdgeV.by4(0.1);
	/* 0.2 */ private final EdgeV TOP4BYP2 = EdgeV.by4(0.2);
    /* 0.25 */ private final EdgeV TOP4BYP25 = EdgeV.by4(0.25);
    /* 0.3 */ private final EdgeV TOP4BYP3 = EdgeV.by4(0.3);
    /* 0.3333... */ private final EdgeV TOP4BYP33 = EdgeV.by4(0.3333333333);
    /* 0.4 */ private final EdgeV TOP4BYP4 = EdgeV.by4(0.4);
    /* 0.5 */ private final EdgeV TOP4BYP5 = EdgeV.by4(0.5);
    /* 0.5833... */ private final EdgeV TOP4BYP5833 = EdgeV.by4(0.5833333333);
    /* 0.6 */ private final EdgeV TOP4BYP6 = EdgeV.by4(0.6);
    /* 0.6666... */ private final EdgeV TOP4BYP66 = EdgeV.by4(0.6666666666);
    /* 0.75 */ private final EdgeV TOP4BYP75 = EdgeV.by4(0.75);
    /* 0.8 */ private final EdgeV TOP4BYP8 = EdgeV.by4(0.8);
    /* 0.9 */ private final EdgeV TOP4BYP9 = EdgeV.by4(0.9);
//	/* 0.1 */			private final EdgeV TOP6BYP1 = EdgeV.by6(0.1);
	/* 0.2 */ private final EdgeV TOP6BYP2 = EdgeV.by6(0.2);
    /* 0.25 */ private final EdgeV TOP6BYP25 = EdgeV.by6(0.25);
    /* 0.3 */ private final EdgeV TOP6BYP3 = EdgeV.by6(0.3);
    /* 0.3333... */ private final EdgeV TOP6BYP33 = EdgeV.by6(0.3333333333);
    /* 0.4 */ private final EdgeV TOP6BYP4 = EdgeV.by6(0.4);
    /* 0.5 */ private final EdgeV TOP6BYP5 = EdgeV.by6(0.5);
    /* 0.5833... */ private final EdgeV TOP6BYP5833 = EdgeV.by6(0.5833333333);
    /* 0.6 */ private final EdgeV TOP6BYP6 = EdgeV.by6(0.6);
    /* 0.6666... */ private final EdgeV TOP6BYP66 = EdgeV.by6(0.6666666666);
    /* 0.75 */ private final EdgeV TOP6BYP75 = EdgeV.by6(0.75);
    /* 0.8 */ private final EdgeV TOP6BYP8 = EdgeV.by6(0.8);
    /* 0.9 */ private final EdgeV TOP6BYP9 = EdgeV.by6(0.9);
//	/* 0.1 */			private final EdgeV TOP10BYP1 = EdgeV.by10(0.1);
	/* 0.2 */ private final EdgeV TOP10BYP2 = EdgeV.by10(0.2);
    /* 0.25 */ private final EdgeV TOP10BYP25 = EdgeV.by10(0.25);
    /* 0.3 */ private final EdgeV TOP10BYP3 = EdgeV.by10(0.3);
    /* 0.3333... */ private final EdgeV TOP10BYP33 = EdgeV.by10(0.3333333333);
    /* 0.4 */ private final EdgeV TOP10BYP4 = EdgeV.by10(0.4);
    /* 0.5 */ private final EdgeV TOP10BYP5 = EdgeV.by10(0.5);
    /* 0.5833... */ private final EdgeV TOP10BYP5833 = EdgeV.by10(0.5833333333);
    /* 0.6 */ private final EdgeV TOP10BYP6 = EdgeV.by10(0.6);
    /* 0.6666... */ private final EdgeV TOP10BYP66 = EdgeV.by10(0.6666666666);
    /* 0.75 */ private final EdgeV TOP10BYP75 = EdgeV.by10(0.75);
    /* 0.8 */ private final EdgeV TOP10BYP8 = EdgeV.by10(0.8);
    /* 0.9 */ private final EdgeV TOP10BYP9 = EdgeV.by10(0.9);

    // -------------------- private and protected methods ------------------------
//	public Schematics(Generic generic, TechFactory techFactory) {
//        super(generic, techFactory, Collections.<TechFactory.Param,Object>emptyMap(),
//                Xml.parseTechnology(Schematics.class.getResource("schematic.xml"), false));
//        arc_lay = findLayer("Arc");
//        bus_lay = findLayer("Bus");
//        node_lay = findLayer("Node");
//        text_lay = findLayer("Text");
//        
//        wire_arc = findArcProto("wire");
//        bus_arc = findArcProto("bus");
//
//        wirePinNode = findNodeProto("Wire_Pin");
//        busPinNode = findNodeProto("Bus_Pin");
//        wireConNode = findNodeProto("Wire_Con");
//        bufferNode = findNodeProto("Buffer");
//        andNode = findNodeProto("And");
//        orNode = findNodeProto("Or");
//        xorNode = findNodeProto("Xor");
//        flipflopNode = findNodeProto("Flip-Flop");
//        muxNode = findNodeProto("Mux");
//        bboxNode = findNodeProto("Bbox");
//        switchNode = findNodeProto("Switch");
//        offpageNode = findNodeProto("Off-Page");
//        powerNode = findNodeProto("Power");
//        groundNode = findNodeProto("Ground");
//        sourceNode = findNodeProto("Source");
//        transistorNode = findNodeProto("Transistor");
//        resistorNode = findNodeProto("Resistor");
//        capacitorNode = findNodeProto("Capacitor");
//        diodeNode = findNodeProto("Diode");
//        inductorNode = findNodeProto("Inductor");
//        meterNode = findNodeProto("Meter");
//        wellNode = findNodeProto("Well");
//        substrateNode = findNodeProto("Substrate");
//        twoportNode = findNodeProto("Two-Port");
//        transistor4Node = findNodeProto("4-Port-Transistor");
//        globalNode = findNodeProto("Global-Signal");
//        globalPartitionNode = findNodeProto("Global-Partition");
//        
//        ffLayers = null;
//        tranLayers = null;
//        twoportLayers = null;
//        offPageLayers = null;
//        offPageInputLayers = null;
//        offPageOutputLayers = null;
//        offPageBidirectionalLayers = null;
//        tran4Layers = null;
//        resistorLayers = null;
//        capacitorLayers = null;
//        diodeLayers = null;
//        
//        xmlSch = null;
//
//    }
    public Schematics(Generic generic, TechFactory techFactory) {
        this(generic, techFactory, true);
    }

    public Schematics(Generic generic, TechFactory techFactory, boolean old) {
        super(generic, techFactory, Foundry.Type.NONE, 1);

        xmlSch = Xml.parseTechnology(Schematics.class.getResource("schematic.xml"), false);

        setTechShortName("Schematics");
        setTechDesc("Schematic Capture");
        setFactoryScale(2000, false);			// in nanometers: really 2 micron
        setNonStandard();
        setStaticTechnology();

//		setFactoryTransparentLayers(new Color []
//   		{
//   			new Color(107, 226, 96)  // Bus
//   		});

        //**************************************** LAYERS ****************************************

        /** arc layer */
        arc_lay = Layer.newInstance(this, "Arc",
                new EGraphics(false, false, null, 0, 0, 0, 255, 0.8, true,
                new int[]{0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF,
                    0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF}));

        /** bus layer */
//		bus_lay = Layer.newInstance(this, "Bus",
//			new EGraphics(false, true, null, EGraphics.TRANSPARENT_1, 107,226,96, 0.8,true,
//			new int[] { 0x2222,   //   X   X   X   X
//				0x0000,   //
//				0x8888,   // X   X   X   X
//				0x0000,   //
//				0x2222,   //   X   X   X   X
//				0x0000,   //
//				0x8888,   // X   X   X   X
//				0x0000,   //
//				0x2222,   //   X   X   X   X
//				0x0000,   //
//				0x8888,   // X   X   X   X
//				0x0000,   //
//				0x2222,   //   X   X   X   X
//				0x0000,   //
//				0x8888,   // X   X   X   X
//				0x0000}));//
        bus_lay = Layer.newInstance(this, "Bus",
                new EGraphics(true, true, null, 0, 0, 255, 0, 0.8, true,
                new int[]{0xAAAA, // X X X X X X X X
                    0x5555, //  X X X X X X X X
                    0xAAAA, // X X X X X X X X
                    0x5555, //  X X X X X X X X
                    0xAAAA, // X X X X X X X X
                    0x5555, //  X X X X X X X X
                    0xAAAA, // X X X X X X X X
                    0x5555, //  X X X X X X X X
                    0xAAAA, // X X X X X X X X
                    0x5555, //  X X X X X X X X
                    0xAAAA, // X X X X X X X X
                    0x5555, //  X X X X X X X X
                    0xAAAA, // X X X X X X X X
                    0x5555, //  X X X X X X X X
                    0xAAAA, // X X X X X X X X
                    0x5555}));//  X X X X X X X X

        /** node layer */
        node_lay = Layer.newInstance(this, "Node",
                new EGraphics(false, false, null, 0, 255, 0, 0, 0.8, true,
                new int[]{0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF,
                    0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF}));

        /** text layer */
        text_lay = Layer.newInstance(this, "Text",
                new EGraphics(false, false, null, 0, 0, 0, 0, 0.8, true,
                new int[]{0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF,
                    0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF}));

        // The layer functions
        arc_lay.setFunction(Layer.Function.METAL1);														// arc
        bus_lay.setFunction(Layer.Function.BUS);														// bus
        node_lay.setFunction(Layer.Function.ART);														// node
        text_lay.setFunction(Layer.Function.ART);														// text


        //**************************************** ARCS ****************************************

        /** wire arc */
        wire_arc = newArcProto("wire", 0, 0.0, ArcProto.Function.METAL1,
                new Technology.ArcLayer(arc_lay, 0, Poly.Type.FILLED));
        wire_arc.setFactoryFixedAngle(true);
        wire_arc.setFactorySlidable(false);
        wire_arc.setFactoryAngleIncrement(45);

        /** bus arc */
        bus_arc = newArcProto("bus", 0, 1.0, ArcProto.Function.BUS,
                new Technology.ArcLayer(bus_lay, 1, Poly.Type.FILLED));
        bus_arc.setFactoryFixedAngle(true);
        bus_arc.setFactorySlidable(false);
        bus_arc.setFactoryExtended(false);
        bus_arc.setFactoryAngleIncrement(45);


        //**************************************** NODES ****************************************

        // this text descriptor is used for all text on nodes
        TextDescriptor tdBig = TextDescriptor.EMPTY.withRelSize(2);
        TextDescriptor tdSmall = TextDescriptor.EMPTY.withRelSize(1);

        /** wire pin */
        wirePinNode = new WirePinNode();

        /** bus pin */
        busPinNode = new BusPinNode();

        /** wire con */
        Technology.NodeLayer letterJ;
        wireConNode = PrimitiveNode.newInstance("Wire_Con", this, 2.0, 2.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(-1), EdgeV.b(-1)),
                        new Technology.TechPoint(EdgeH.r(1), EdgeV.t(1))
                    }),
                    letterJ = new Technology.NodeLayer(text_lay, 0, Poly.Type.TEXTCENT, Technology.NodeLayer.POINTS, Technology.TechPoint.makeCenterBox())
                });
        PrimitivePort wireCon_port = PrimitivePort.newInstance(wireConNode, new ArcProto[]{wire_arc, bus_arc}, "wire", true,
                0, 180, 0, PortCharacteristic.UNKNOWN, true, false,
                EdgeH.l(-0.5), EdgeV.b(-0.5), EdgeH.r(0.5), EdgeV.t(0.5));
        wireConNode.addPrimitivePorts(wireCon_port);
        wireConNode.setFunction(PrimitiveNode.Function.CONNECT);
        letterJ.setMessage("J");
        letterJ.setDescriptor(tdBig);

        /** general buffer */
        bufferNode = PrimitiveNode.newInstance("Buffer", this, 6.0, 6.0, ERectangle.fromLambda(-3, -2.75, 5, 5.5),
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS,
                    new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.l(-3), EdgeV.t(2.75)),
                        new Technology.TechPoint(EdgeH.l(-3), EdgeV.b(-2.75))
                    })
                });
        PrimitivePort bufferInPort = PrimitivePort.newInstance(bufferNode, new ArcProto[]{wire_arc, bus_arc}, "a", false,
                180, 45, 0, PortCharacteristic.IN, false, true,
                EdgeH.l(-3), EdgeV.c(0), EdgeH.l(-3), EdgeV.c(0));
        PrimitivePort bufferSidePort = PrimitivePort.newInstance(bufferNode, new ArcProto[]{wire_arc}, "c", false,
                270, 45, 1, PortCharacteristic.IN, false, true,
                EdgeH.c(0), EdgeV.b(-1), EdgeH.c(0), EdgeV.b(-1));
        PrimitivePort bufferOutPort = PrimitivePort.newInstance(bufferNode, new ArcProto[]{wire_arc, bus_arc}, "y", false,
                0, 45, 2, PortCharacteristic.OUT, false, true,
                EdgeH.r(2), EdgeV.c(0), EdgeH.r(2), EdgeV.c(0));
        bufferNode.addPrimitivePorts(bufferInPort, bufferSidePort, bufferOutPort);
        bufferNode.setFunction(PrimitiveNode.Function.BUFFER);

        /** general and */
        andNode = new AndOrXorNode("And", 8.0, 6.0, ERectangle.fromLambda(-4, -3, 7.5, 6),
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0.5), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0.5), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(0.5), EdgeV.c(-3))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS,
                    new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0.5), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.t(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.b(-3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(-3)),
                        new Technology.TechPoint(EdgeH.c(0.5), EdgeV.c(-3))
                    })
                });
        PrimitivePort andInPort = new SpecialSelectionPort(andNode, "a",
            EdgeH.c(-4), EdgeV.b(-3), EdgeH.c(-4), EdgeV.t(3));
        PrimitivePort andOutPort = new SquareSizablePort(andNode, new ArcProto[]{wire_arc, bus_arc}, "y",
            0, 45, 1, PortCharacteristic.OUT, EdgeH.c(3.5), EdgeV.c(0), EdgeH.c(3.5), EdgeV.c(0));
        PrimitivePort andTopPort = new SquareSizablePort(andNode, new ArcProto[]{wire_arc, bus_arc}, "yt",
            0, 45, 2, PortCharacteristic.OUT, EdgeH.c(2.75), EdgeV.c(2), EdgeH.c(2.75), EdgeV.c(2));
        PrimitivePort andBottomPort = new SquareSizablePort(andNode, new ArcProto[]{wire_arc, bus_arc}, "yc",
            0, 45, 3, PortCharacteristic.OUT, EdgeH.c(2.75), EdgeV.c(-2), EdgeH.c(2.75), EdgeV.c(-2));
        andNode.addPrimitivePorts(andInPort, andOutPort, andTopPort, andBottomPort);
        andNode.setFunction(PrimitiveNode.Function.GATEAND);
        andNode.setAutoGrowth(0, 4);

        /** general or */
        orNode = new AndOrXorNode("Or", 10.0, 6.0, ERectangle.fromLambda(-4, -3, 8.5, 6),
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(-9), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(-3))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(-4.5)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(-3)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(4.5), EdgeV.c(0))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(4.5)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(4.5), EdgeV.c(0)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(-3))}),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(-3))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.t(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(3)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.b(-3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(-3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(-3)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(-3))
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(-3))
                    })
                });
        PrimitivePort orInPort = new SpecialSelectionPort(orNode, "a",
            EdgeH.c(-4), EdgeV.b(-3), EdgeH.c(-3), EdgeV.t(3));
        PrimitivePort orOutPort = new SquareSizablePort(orNode, new ArcProto[]{wire_arc, bus_arc}, "y",
            0, 45, 1, PortCharacteristic.OUT, EdgeH.c(4.5), EdgeV.c(0), EdgeH.c(4.5), EdgeV.c(0));
        PrimitivePort orTopPort = new SquareSizablePort(orNode, new ArcProto[]{wire_arc, bus_arc}, "yt",
        	0, 45, 2, PortCharacteristic.OUT, EdgeH.c(2.65), EdgeV.c(2), EdgeH.c(2.65), EdgeV.c(2));
        PrimitivePort orBottomPort = new SquareSizablePort(orNode, new ArcProto[]{wire_arc, bus_arc}, "yc",
        	0, 45, 3, PortCharacteristic.OUT, EdgeH.c(2.65), EdgeV.c(-2), EdgeH.c(2.65), EdgeV.c(-2));
        orNode.addPrimitivePorts(orInPort, orOutPort, orTopPort, orBottomPort);
        orNode.setFunction(PrimitiveNode.Function.GATEOR);
        orNode.setAutoGrowth(0, 4);

        /** general xor */
        xorNode = new AndOrXorNode("Xor", 10.0, 6.0, ERectangle.fromLambda(-5, -3, 9.5, 6),
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(-9), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(-3))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(-4.5)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(-3)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(4.5), EdgeV.c(0))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(4.5)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(4.5), EdgeV.c(0)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(-3))}),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(-3))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(-10), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(-5), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-5), EdgeV.c(-3))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.t(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(3)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.b(-3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(-3)),
                        new Technology.TechPoint(EdgeH.c(-4), EdgeV.c(-3)),
                        //					new Technology.TechPoint(EdgeH.c(-1.5), EdgeV.c(-3))
                        new Technology.TechPoint(EdgeH.c(-0.75), EdgeV.c(-3))
                    })
                });
        PrimitivePort xorInPort = new SpecialSelectionPort(xorNode, "a",
            EdgeH.c(-4), EdgeV.b(-3), EdgeH.c(-3), EdgeV.t(3));
        PrimitivePort xorOutPort = new SquareSizablePort(xorNode, new ArcProto[]{wire_arc, bus_arc}, "y",
        	0, 45, 1, PortCharacteristic.OUT, EdgeH.c(4.5), EdgeV.c(0), EdgeH.c(4.5), EdgeV.c(0));
        PrimitivePort xorTopPort = new SquareSizablePort(xorNode, new ArcProto[]{wire_arc, bus_arc}, "yt",
        	0, 45, 2, PortCharacteristic.OUT, EdgeH.c(2.65), EdgeV.c(2), EdgeH.c(2.65), EdgeV.c(2));
        PrimitivePort xorBottomPort = new SquareSizablePort(xorNode, new ArcProto[]{wire_arc, bus_arc}, "yc",
        	0, 45, 3, PortCharacteristic.OUT, EdgeH.c(2.65), EdgeV.c(-2), EdgeH.c(2.65), EdgeV.c(-2));
        xorNode.addPrimitivePorts(xorInPort, xorOutPort, xorTopPort, xorBottomPort);
        xorNode.setFunction(PrimitiveNode.Function.GATEXOR);
        xorNode.setAutoGrowth(0, 4);

        /** general flip flop */
        Technology.NodeLayer ffBox = new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), EdgeV.b(-5)),
                    new Technology.TechPoint(EdgeH.r(3), EdgeV.t(5))
                });
        Technology.NodeLayer ffArrow = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), BOT10BYP2),
                    new Technology.TechPoint(LEFT6BYP7, EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP2)});
        Technology.NodeLayer ffWaveformN = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP6, TOP10BYP2),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP2),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP2),
                    new Technology.TechPoint(LEFT6BYP2, BOT10BYP2)});
        Technology.NodeLayer ffWaveformP = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP6, BOT10BYP2),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP2),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP2),
                    new Technology.TechPoint(LEFT6BYP2, TOP10BYP2)});
        Technology.NodeLayer ffWaveformMS = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP6, BOT10BYP2),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP2),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP2),
                    new Technology.TechPoint(LEFT6BYP2, TOP10BYP2),
                    new Technology.TechPoint(LEFT6BYP2, BOT10BYP2),
                    new Technology.TechPoint(EdgeH.c(0), BOT10BYP2)});
        Technology.NodeLayer ffLetterD = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP4),
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP4)});
        ffLetterD.setMessage("D");
        ffLetterD.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterR = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP4),
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP4)});
        ffLetterR.setMessage("R");
        ffLetterR.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterJ = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP4),
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP4)});
        ffLetterJ.setMessage("J");
        ffLetterJ.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterT = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP4),
                    new Technology.TechPoint(EdgeH.l(-3), TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, TOP10BYP4)});
        ffLetterT.setMessage("T");
        ffLetterT.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterE = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), BOT10BYP4),
                    new Technology.TechPoint(EdgeH.l(-3), BOT10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP4)});
        ffLetterE.setMessage("E");
        ffLetterE.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterS = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), BOT10BYP4),
                    new Technology.TechPoint(EdgeH.l(-3), BOT10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP4)});
        ffLetterS.setMessage("S");
        ffLetterS.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterK = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), BOT10BYP4),
                    new Technology.TechPoint(EdgeH.l(-3), BOT10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP8),
                    new Technology.TechPoint(LEFT6BYP4, BOT10BYP4)});
        ffLetterK.setMessage("K");
        ffLetterK.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterQ = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.r(3), TOP10BYP4),
                    new Technology.TechPoint(EdgeH.r(3), TOP10BYP8),
                    new Technology.TechPoint(RIGHT6BYP4, TOP10BYP8),
                    new Technology.TechPoint(RIGHT6BYP4, TOP10BYP4)});
        ffLetterQ.setMessage("Q");
        ffLetterQ.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterQB = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.r(3), BOT10BYP4),
                    new Technology.TechPoint(EdgeH.r(3), BOT10BYP8),
                    new Technology.TechPoint(RIGHT6BYP4, BOT10BYP8),
                    new Technology.TechPoint(RIGHT6BYP4, BOT10BYP4)});
        ffLetterQB.setMessage("QB");
        ffLetterQB.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterPR = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP6, TOP10BYP6),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.t(5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.t(5)),
                    new Technology.TechPoint(RIGHT6BYP6, TOP10BYP6)});
        ffLetterPR.setMessage("PR");
        ffLetterPR.setDescriptor(tdSmall);
        Technology.NodeLayer ffLetterCLR = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP6, BOT10BYP6),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.b(-5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.b(-5)),
                    new Technology.TechPoint(RIGHT6BYP6, BOT10BYP6)});
        ffLetterCLR.setMessage("CLR");
        ffLetterCLR.setDescriptor(tdSmall);
        Technology.NodeLayer[] ffLayersRSMS = new Technology.NodeLayer[]{
            ffWaveformMS, ffLetterR, ffLetterS,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersRSP = new Technology.NodeLayer[]{
            ffWaveformP, ffLetterR, ffLetterS,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersRSN = new Technology.NodeLayer[]{
            ffWaveformN, ffLetterR, ffLetterS,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersJKMS = new Technology.NodeLayer[]{
            ffWaveformMS, ffLetterJ, ffLetterK,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersJKP = new Technology.NodeLayer[]{
            ffWaveformP, ffLetterJ, ffLetterK,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersJKN = new Technology.NodeLayer[]{
            ffWaveformN, ffLetterJ, ffLetterK,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersDMS = new Technology.NodeLayer[]{
            ffWaveformMS, ffLetterD, ffLetterE,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersDP = new Technology.NodeLayer[]{
            ffWaveformP, ffLetterD, ffLetterE,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersDN = new Technology.NodeLayer[]{
            ffWaveformN, ffLetterD, ffLetterE,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersTMS = new Technology.NodeLayer[]{
            ffWaveformMS, ffLetterT,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersTP = new Technology.NodeLayer[]{
            ffWaveformP, ffLetterT,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[] ffLayersTN = new Technology.NodeLayer[]{
            ffWaveformN, ffLetterT,
            ffBox, ffArrow, ffLetterQ, ffLetterQB, ffLetterPR, ffLetterCLR
        };
        Technology.NodeLayer[][] ffLayers = {
            ffLayersRSMS, ffLayersJKMS, ffLayersDMS, ffLayersTMS,
            ffLayersRSP, ffLayersJKP, ffLayersDP, ffLayersTP,
            ffLayersRSN, ffLayersJKN, ffLayersDN, ffLayersTN
        };
        PrimitiveNode.Function[] ffFunctions = {
            PrimitiveNode.Function.FLIPFLOPRSMS, PrimitiveNode.Function.FLIPFLOPJKMS, PrimitiveNode.Function.FLIPFLOPDMS, PrimitiveNode.Function.FLIPFLOPTMS,
            PrimitiveNode.Function.FLIPFLOPRSP, PrimitiveNode.Function.FLIPFLOPJKP, PrimitiveNode.Function.FLIPFLOPDP, PrimitiveNode.Function.FLIPFLOPTP,
            PrimitiveNode.Function.FLIPFLOPRSN, PrimitiveNode.Function.FLIPFLOPJKN, PrimitiveNode.Function.FLIPFLOPDN, PrimitiveNode.Function.FLIPFLOPTN
        };
        flipflopNode = new MultiFunctionNode("Flip-Flop", 6.0, 10.0, ERectangle.fromLambda(-3, -5, 6, 10), ffLayersRSMS, ffLayers, PrimitiveNode.Function.FLIPFLOPRSMS, ffFunctions);
        PrimitivePort flipflopI1 = PrimitivePort.newInstance(flipflopNode, new ArcProto[]{wire_arc}, "i1", false,
                180, 45, 0, PortCharacteristic.IN, false, true,
                EdgeH.l(-3), TOP10BYP6, EdgeH.l(-3), TOP10BYP6);
        PrimitivePort flipflopI2 = PrimitivePort.newInstance(flipflopNode, new ArcProto[]{wire_arc}, "i2", false,
                180, 45, 1, PortCharacteristic.IN, false, true,
                EdgeH.l(-3), BOT10BYP6, EdgeH.l(-3), BOT10BYP6);
        PrimitivePort flipflopQ = PrimitivePort.newInstance(flipflopNode, new ArcProto[]{wire_arc}, "q", false,
                0, 45, 2, PortCharacteristic.OUT, false, true,
                EdgeH.r(3), TOP10BYP6, EdgeH.r(3), TOP10BYP6);
        PrimitivePort flipflopQB = PrimitivePort.newInstance(flipflopNode, new ArcProto[]{wire_arc}, "qb", false,
                0, 45, 3, PortCharacteristic.OUT, false, true,
                EdgeH.r(3), BOT10BYP6, EdgeH.r(3), BOT10BYP6);
        PrimitivePort flipflopCK = PrimitivePort.newInstance(flipflopNode, new ArcProto[]{wire_arc}, "ck", false,
                180, 45, 4, PortCharacteristic.IN, false, true,
                EdgeH.l(-3), EdgeV.c(0), EdgeH.l(-3), EdgeV.c(0));
        PrimitivePort flipflopPRE = PrimitivePort.newInstance(flipflopNode, new ArcProto[]{wire_arc}, "preset", false,
                90, 45, 5, PortCharacteristic.IN, false, true,
                EdgeH.c(0), EdgeV.t(5), EdgeH.c(0), EdgeV.t(5));
        PrimitivePort flipflopCLR = PrimitivePort.newInstance(flipflopNode, new ArcProto[]{wire_arc}, "clear", false,
                270, 45, 6, PortCharacteristic.IN, false, true,
                EdgeH.c(0), EdgeV.b(-5), EdgeH.c(0), EdgeV.b(-5));
        flipflopNode.addPrimitivePorts(flipflopI1, flipflopI2, flipflopQ, flipflopQB, flipflopCK, flipflopPRE, flipflopCLR);

        /** mux */
        muxNode = PrimitiveNode.newInstance("Mux", this, 8.0, 10.0, ERectangle.fromLambda(-3.5, -5, 7, 10),
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS,
                    new Technology.TechPoint[]{
                        new Technology.TechPoint(RIGHT8BYP8, TOP10BYP75),
                        new Technology.TechPoint(RIGHT8BYP8, BOT10BYP75),
                        new Technology.TechPoint(LEFT8BYP8, EdgeV.b(-5)),
                        new Technology.TechPoint(LEFT8BYP8, EdgeV.t(5))
                    })
                });
        PrimitivePort muxInPort = new SpecialSelectionPort(muxNode, "a",
                LEFT8BYP8, EdgeV.b(-5), LEFT8BYP8, EdgeV.t(5));
        PrimitivePort muxSidePort = PrimitivePort.newInstance(muxNode, new ArcProto[]{wire_arc}, "s", false,
                270, 45, 2, PortCharacteristic.IN, false, true,
                EdgeH.c(0), BOT10BYP875, EdgeH.c(0), BOT10BYP875);
        PrimitivePort muxOutPort = PrimitivePort.newInstance(muxNode, new ArcProto[]{wire_arc, bus_arc}, "y", false,
                0, 45, 1, PortCharacteristic.OUT, false, true,
                RIGHT8BYP8, EdgeV.c(0), RIGHT8BYP8, EdgeV.c(0));
        muxNode.addPrimitivePorts(muxInPort, muxSidePort, muxOutPort);
        muxNode.setFunction(PrimitiveNode.Function.MUX);
        muxNode.setAutoGrowth(0, 4);

        /** black box */
        bboxNode = PrimitiveNode.newInstance("Bbox", this, 10.0, 10.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX,
                    new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(-5), EdgeV.b(-5)),
                        new Technology.TechPoint(EdgeH.r(5), EdgeV.t(5))
                    })
                });
        PrimitivePort bbox_port1 = PrimitivePort.newInstance(bboxNode, new ArcProto[]{wire_arc, bus_arc}, "a", false,
                0, 45, 0, PortCharacteristic.UNKNOWN, true, true,
                EdgeH.r(5), EdgeV.b(-5), EdgeH.r(5), EdgeV.t(5));
        PrimitivePort bbox_port2 = PrimitivePort.newInstance(bboxNode, new ArcProto[]{wire_arc, bus_arc}, "b", false,
                90, 45, 1, PortCharacteristic.UNKNOWN, true, true,
                EdgeH.l(-5), EdgeV.t(5), EdgeH.r(5), EdgeV.t(5));
        PrimitivePort bbox_port3 = PrimitivePort.newInstance(bboxNode, new ArcProto[]{wire_arc, bus_arc}, "c", false,
                180, 45, 2, PortCharacteristic.UNKNOWN, true, true,
                EdgeH.l(-5), EdgeV.b(-5), EdgeH.l(-5), EdgeV.t(5));
        PrimitivePort bbox_port4 = PrimitivePort.newInstance(bboxNode, new ArcProto[]{wire_arc, bus_arc}, "d", false,
                270, 45, 3, PortCharacteristic.UNKNOWN, true, true,
                EdgeH.l(-5), EdgeV.b(-5), EdgeH.r(5), EdgeV.b(-5));
        bboxNode.addPrimitivePorts(bbox_port1, bbox_port2, bbox_port3, bbox_port4);
        bboxNode.setFunction(PrimitiveNode.Function.UNKNOWN);

        /** switch */
        switchNode = new SwitchNode();

        /** off page connector */
        offpageNode = new OffPageNode();

        /** power */
        powerNode = new ExtraBlobsNode("Power", 3.0, 3.0,
                new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(1.5))}),
                new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), TOP3BYP75)}));
        powerNode.addPrimitivePorts(
                PrimitivePort.single(powerNode, new ArcProto[]{wire_arc}, "vdd", 0, 180, 0, PortCharacteristic.PWR,
                EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0)));
        powerNode.setFunction(PrimitiveNode.Function.CONPOWER);
        powerNode.setSquare();

        /** ground */
        groundNode = new ExtraBlobsNode("Ground", 3.0, 4.0,
                new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)), new Technology.TechPoint(EdgeH.c(0), EdgeV.t(2)),
                    new Technology.TechPoint(EdgeH.l(-1.5), EdgeV.c(0)), new Technology.TechPoint(EdgeH.r(1.5), EdgeV.c(0)),
                    new Technology.TechPoint(LEFT3BYP75, BOT4BYP25), new Technology.TechPoint(RIGHT3BYP75, BOT4BYP25),
                    new Technology.TechPoint(LEFT3BYP5, BOT4BYP5), new Technology.TechPoint(RIGHT3BYP5, BOT4BYP5),
                    new Technology.TechPoint(LEFT3BYP25, BOT4BYP75), new Technology.TechPoint(RIGHT3BYP25, BOT4BYP75),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2)), new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2))}));
        groundNode.addPrimitivePorts(
                PrimitivePort.single(groundNode, new ArcProto[]{wire_arc}, "gnd", 90, 90, 0, PortCharacteristic.GND,
                EdgeH.c(0), EdgeV.t(2), EdgeH.c(0), EdgeV.t(2)));
        groundNode.setFunction(PrimitiveNode.Function.CONGROUND);

        /** source */
        sourceNode = new ExtraBlobsNode("Source", 6.0, 6.0,
                new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.r(3), EdgeV.c(0))}),
                new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP3, TOP6BYP6), new Technology.TechPoint(RIGHT6BYP3, TOP6BYP6),
                    new Technology.TechPoint(EdgeH.c(0), TOP6BYP3), new Technology.TechPoint(EdgeH.c(0), TOP6BYP9)}));
        sourceNode.addPrimitivePorts(
                PrimitivePort.newInstance(sourceNode, new ArcProto[]{wire_arc}, "plus", 90, 45, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(3), EdgeH.c(0), EdgeV.t(3)),
                PrimitivePort.newInstance(sourceNode, new ArcProto[]{wire_arc}, "minus", 270, 45, 1, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.b(-3), EdgeH.c(0), EdgeV.b(-3)));
        sourceNode.setFunction(PrimitiveNode.Function.SOURCE);
        sourceNode.setSquare();

        /** transistor */
        Technology.NodeLayer tranLayerTranTop = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP75, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP75, BOT4BYP25)});
        Technology.NodeLayer tranLayerNMOS = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), BOT4BYP25),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(1))});
        Technology.NodeLayer tranLayerBTran1 = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP25, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP25, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(EdgeH.r(2), EdgeV.b(-2))});
        Technology.NodeLayer tranLayerBTran2 = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP75, BOT4BYP75),
                    new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP5, BOT4BYP875)});
        Technology.NodeLayer tranLayerBTran3 = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP5, BOT4BYP375),
                    new Technology.TechPoint(LEFT4BYP25, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP25, BOT4BYP5)});
        Technology.NodeLayer tranLayerBTran4 = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP75, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP875, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP875, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP75, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(RIGHT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(EdgeH.r(2), EdgeV.b(-2))});
        Technology.NodeLayer tranLayerBTran5 = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP125, EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP125, EdgeV.c(0))});
        Technology.NodeLayer tranLayerBTran6 = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP125, EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), TOP4BYP25),
                    new Technology.TechPoint(RIGHT4BYP125, EdgeV.c(0))});
        Technology.NodeLayer tranLayerBTran7 = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP75, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP875, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP5, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP25, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP25, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP5, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP875, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP75, BOT4BYP25),
                    new Technology.TechPoint(RIGHT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(RIGHT4BYP75, EdgeV.b(-2)),
                    new Technology.TechPoint(EdgeH.r(2), EdgeV.b(-2))});
        Technology.NodeLayer[] tranLayersN = buildTransistorDescription(true, false, false, false, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersP = buildTransistorDescription(false, false, false, false, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersNd = buildTransistorDescription(true, true, false, false, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersPd = buildTransistorDescription(false, true, false, false, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersNnT = buildTransistorDescription(true, false, true, false, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersPnT = buildTransistorDescription(false, false, true, false, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersNfG = buildTransistorDescription(true, false, false, true, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersPfG = buildTransistorDescription(false, false, false, true, 0, 0, false, false);
        Technology.NodeLayer[] tranLayersNCN = buildTransistorDescription(true, false, false, false, 0, 0, false, true);
        Technology.NodeLayer[] tranLayersPCN = buildTransistorDescription(false, false, false, false, 0, 0, false, true);
        Technology.NodeLayer[] tranLayersNvtL = buildTransistorDescription(true, false, false, false, -1, 0, false, false);
        Technology.NodeLayer[] tranLayersPvtL = buildTransistorDescription(false, false, false, false, -1, 0, false, false);
        Technology.NodeLayer[] tranLayersNvtH = buildTransistorDescription(true, false, false, false, 1, 0, false, false);
        Technology.NodeLayer[] tranLayersPvtH = buildTransistorDescription(false, false, false, false, 1, 0, false, false);
        Technology.NodeLayer[] tranLayersNht1 = buildTransistorDescription(true, false, false, false, 0, 1, false, false);
        Technology.NodeLayer[] tranLayersPht1 = buildTransistorDescription(false, false, false, false, 0, 1, false, false);
        Technology.NodeLayer[] tranLayersNht2 = buildTransistorDescription(true, false, false, false, 0, 2, false, false);
        Technology.NodeLayer[] tranLayersPht2 = buildTransistorDescription(false, false, false, false, 0, 2, false, false);
        Technology.NodeLayer[] tranLayersNht3 = buildTransistorDescription(true, false, false, false, 0, 3, false, false);
        Technology.NodeLayer[] tranLayersPht3 = buildTransistorDescription(false, false, false, false, 0, 3, false, false);
        Technology.NodeLayer[] tranLayersNnTht1 = buildTransistorDescription(true, false, true, false, 0, 1, false, false);
        Technology.NodeLayer[] tranLayersPnTht1 = buildTransistorDescription(false, false, true, false, 0, 1, false, false);
        Technology.NodeLayer[] tranLayersNnTht2 = buildTransistorDescription(true, false, true, false, 0, 2, false, false);
        Technology.NodeLayer[] tranLayersPnTht2 = buildTransistorDescription(false, false, true, false, 0, 2, false, false);
        Technology.NodeLayer[] tranLayersNnTht3 = buildTransistorDescription(true, false, true, false, 0, 3, false, false);
        Technology.NodeLayer[] tranLayersPnTht3 = buildTransistorDescription(false, false, true, false, 0, 3, false, false);
        Technology.NodeLayer[] tranLayersNPN = new Technology.NodeLayer[]{tranLayerBTran1, tranLayerTranTop, tranLayerNMOS, tranLayerBTran2};
        Technology.NodeLayer[] tranLayersPNP = new Technology.NodeLayer[]{tranLayerBTran1, tranLayerTranTop, tranLayerNMOS, tranLayerBTran3};
        Technology.NodeLayer[] tranLayersNJFET = new Technology.NodeLayer[]{tranLayerBTran4, tranLayerTranTop, tranLayerNMOS, tranLayerBTran5};
        Technology.NodeLayer[] tranLayersPJFET = new Technology.NodeLayer[]{tranLayerBTran4, tranLayerTranTop, tranLayerNMOS, tranLayerBTran6};
        Technology.NodeLayer[] tranLayersDMES = new Technology.NodeLayer[]{tranLayerBTran4, tranLayerTranTop, tranLayerNMOS};
        Technology.NodeLayer[] tranLayersEMES = new Technology.NodeLayer[]{tranLayerBTran7, tranLayerNMOS};
        Technology.NodeLayer[][] tranLayers = {
            tranLayersN,
            tranLayersNd,
            tranLayersP,
            tranLayersNPN,
            tranLayersPNP,
            tranLayersNJFET,
            tranLayersPJFET,
            tranLayersDMES,
            tranLayersEMES,
            tranLayersPd,
            tranLayersNnT,
            tranLayersPnT,
            tranLayersNfG,
            tranLayersPfG,
            tranLayersNvtL,
            tranLayersPvtL,
            tranLayersNvtH,
            tranLayersPvtH,
            tranLayersNht1,
            tranLayersPht1,
            tranLayersNht2,
            tranLayersPht2,
            tranLayersNht3,
            tranLayersPht3,
            tranLayersNnTht1,
            tranLayersPnTht1,
            tranLayersNnTht2,
            tranLayersPnTht2,
            tranLayersNnTht3,
            tranLayersPnTht3,
            tranLayersNCN,
            tranLayersPCN
        };
        PrimitiveNode.Function[] tranFunctions = {
            PrimitiveNode.Function.TRANMOS,
            PrimitiveNode.Function.TRADMOS,
            PrimitiveNode.Function.TRAPMOS,
            PrimitiveNode.Function.TRANPN,
            PrimitiveNode.Function.TRAPNP,
            PrimitiveNode.Function.TRANJFET,
            PrimitiveNode.Function.TRAPJFET,
            PrimitiveNode.Function.TRADMES,
            PrimitiveNode.Function.TRAEMES,
            PrimitiveNode.Function.TRAPMOSD,
            PrimitiveNode.Function.TRANMOSNT,
            PrimitiveNode.Function.TRAPMOSNT,
            PrimitiveNode.Function.TRANMOSFG,
            PrimitiveNode.Function.TRAPMOSFG,
            PrimitiveNode.Function.TRANMOSVTL,
            PrimitiveNode.Function.TRAPMOSVTL,
            PrimitiveNode.Function.TRANMOSVTH,
            PrimitiveNode.Function.TRAPMOSVTH,
            PrimitiveNode.Function.TRANMOSHV1,
            PrimitiveNode.Function.TRAPMOSHV1,
            PrimitiveNode.Function.TRANMOSHV2,
            PrimitiveNode.Function.TRAPMOSHV2,
            PrimitiveNode.Function.TRANMOSHV3,
            PrimitiveNode.Function.TRAPMOSHV3,
            PrimitiveNode.Function.TRANMOSNTHV1,
            PrimitiveNode.Function.TRAPMOSNTHV1,
            PrimitiveNode.Function.TRANMOSNTHV2,
            PrimitiveNode.Function.TRAPMOSNTHV2,
            PrimitiveNode.Function.TRANMOSNTHV3,
            PrimitiveNode.Function.TRAPMOSNTHV3,
            PrimitiveNode.Function.TRANMOSCN,
            PrimitiveNode.Function.TRAPMOSCN
        };
        transistorNode = new MultiFunctionNode("Transistor", 4.0, 4.0, ERectangle.fromLambda(-2, -2, 4, 3), tranLayersN, tranLayers,
                PrimitiveNode.Function.TRANS, tranFunctions);
        transistorNode.addPrimitivePorts(
                PrimitivePort.newInstance(transistorNode, new ArcProto[]{wire_arc}, "g", 0, 180, 0, PortCharacteristic.IN,
                EdgeH.c(0), EdgeV.t(1), EdgeH.c(0), EdgeV.t(1)),
                PrimitivePort.newInstance(transistorNode, new ArcProto[]{wire_arc}, "s", 180, 90, 1, PortCharacteristic.BIDIR,
                EdgeH.l(-2), EdgeV.b(-2), EdgeH.l(-2), EdgeV.b(-2)),
                PrimitivePort.newInstance(transistorNode, new ArcProto[]{wire_arc}, "d", 0, 90, 2, PortCharacteristic.BIDIR,
                EdgeH.r(2), EdgeV.b(-2), EdgeH.r(2), EdgeV.b(-2)));

        /** resistor */
        Technology.NodeLayer resistorLayer = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP66, EdgeV.c(0)),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.c(0)),
                    new Technology.TechPoint(LEFT6BYP5, EdgeV.t(0.5)),
                    new Technology.TechPoint(LEFT6BYP3, EdgeV.b(-0.5)),
                    new Technology.TechPoint(LEFT6BYP1, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP1, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP3, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP5, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT6BYP66, EdgeV.c(0))});
        /* bold resistor */
        Technology.NodeLayer resistorLayerBold = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENEDT3, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP66, EdgeV.c(0)),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.c(0)),
                    new Technology.TechPoint(LEFT6BYP5, EdgeV.t(0.5)),
                    new Technology.TechPoint(LEFT6BYP3, EdgeV.b(-0.5)),
                    new Technology.TechPoint(LEFT6BYP1, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP1, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP3, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP5, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT6BYP66, EdgeV.c(0))});
        Technology.NodeLayer resistorLayerWell = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENEDT2, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.t(0.5))});
        Technology.NodeLayer resistorLayerActive = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENEDT3, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.b(-0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(LEFT6BYP6, EdgeV.t(0.5))});
        /* P letter */
        Technology.NodeLayer resistorLayerP = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.c(0))});
        resistorLayerP.setMessage("P");
        resistorLayerP.setDescriptor(tdBig);
        /* N letter */
        Technology.NodeLayer resistorLayerN = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.c(0))});
        resistorLayerN.setMessage("N");
        resistorLayerN.setDescriptor(tdBig);
        /* US-N string */
        Technology.NodeLayer resistorUSP = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.c(0))});
        resistorUSP.setMessage("US-P");
        resistorUSP.setDescriptor(tdBig);
        /* US-N string */
        Technology.NodeLayer resistorUSN = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT6BYP4, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.t(0.5)),
                    new Technology.TechPoint(RIGHT6BYP6, EdgeV.c(0))});
        resistorUSN.setMessage("US-N");
        resistorUSN.setDescriptor(tdBig);
        Technology.NodeLayer[] resistorLayersNorm = new Technology.NodeLayer[]{resistorLayer};
        Technology.NodeLayer[] resistorLayersHiResPoly2 = new Technology.NodeLayer[]{resistorLayerBold}; // bold icon
        Technology.NodeLayer[] resistorLayersNPoly = new Technology.NodeLayer[]{resistorLayer, resistorLayerN};
        Technology.NodeLayer[] resistorLayersPPoly = new Technology.NodeLayer[]{resistorLayer, resistorLayerP};
        Technology.NodeLayer[] resistorLayersNNSPoly = new Technology.NodeLayer[]{resistorLayer, resistorUSN};
        Technology.NodeLayer[] resistorLayersPNSPoly = new Technology.NodeLayer[]{resistorLayer, resistorUSP};
        Technology.NodeLayer[] resistorLayersNWell = new Technology.NodeLayer[]{resistorLayer, resistorLayerN, resistorLayerWell};
        Technology.NodeLayer[] resistorLayersPWell = new Technology.NodeLayer[]{resistorLayer, resistorLayerP, resistorLayerWell};
        Technology.NodeLayer[] resistorLayersNActive = new Technology.NodeLayer[]{resistorLayer, resistorLayerN, resistorLayerActive};
        Technology.NodeLayer[] resistorLayersPActive = new Technology.NodeLayer[]{resistorLayer, resistorLayerP, resistorLayerActive};
        Technology.NodeLayer[][] resistorLayers = {
            resistorLayersNorm,
            resistorLayersNPoly,
            resistorLayersPPoly,
            resistorLayersNWell,
            resistorLayersPWell,
            resistorLayersNActive,
            resistorLayersPActive,
            resistorLayersNNSPoly,
            resistorLayersPNSPoly,
            resistorLayersHiResPoly2
        };
        PrimitiveNode.Function[] resistorFunctions = {
            PrimitiveNode.Function.RESIST,
            PrimitiveNode.Function.RESNPOLY,
            PrimitiveNode.Function.RESPPOLY,
            PrimitiveNode.Function.RESNWELL,
            PrimitiveNode.Function.RESPWELL,
            PrimitiveNode.Function.RESNACTIVE,
            PrimitiveNode.Function.RESPACTIVE,
            PrimitiveNode.Function.RESNNSPOLY,
            PrimitiveNode.Function.RESPNSPOLY,
            PrimitiveNode.Function.RESHIRESPOLY2
        };
        resistorNode = new MultiFunctionNode("Resistor", 6.0, 1.0, ERectangle.fromLambda(-2, -0.5, 4, 1),
                resistorLayersNorm, resistorLayers, PrimitiveNode.Function.RESIST, resistorFunctions);
        resistorNode.addPrimitivePorts(
                PrimitivePort.newInstance(resistorNode, new ArcProto[]{wire_arc}, "a", 180, 90, 0, PortCharacteristic.UNKNOWN,
                LEFT6BYP66, EdgeV.c(0), LEFT6BYP66, EdgeV.c(0)),
                PrimitivePort.newInstance(resistorNode, new ArcProto[]{wire_arc}, "b", 0, 90, 1, PortCharacteristic.UNKNOWN,
                RIGHT6BYP66, EdgeV.c(0), RIGHT6BYP66, EdgeV.c(0)));

        /** capacitor */
        Technology.NodeLayer capacitorLayer = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-1.5), TOP4BYP2),
                    new Technology.TechPoint(EdgeH.r(1.5), TOP4BYP2),
                    new Technology.TechPoint(EdgeH.l(-1.5), BOT4BYP2),
                    new Technology.TechPoint(EdgeH.r(1.5), BOT4BYP2),
                    new Technology.TechPoint(EdgeH.c(0), TOP4BYP2),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(2)),
                    new Technology.TechPoint(EdgeH.c(0), BOT4BYP2),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2))});
        Technology.NodeLayer capacitorLayerEl = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT3BYP2, BOT4BYP6),
                    new Technology.TechPoint(RIGHT3BYP6, BOT4BYP6),
                    new Technology.TechPoint(RIGHT3BYP4, BOT4BYP4),
                    new Technology.TechPoint(RIGHT3BYP4, BOT4BYP8)});
        Technology.NodeLayer capacitorLayerPoly2 = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENEDT3, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-1.5), TOP4BYP2),
                    new Technology.TechPoint(EdgeH.r(1.5), TOP4BYP2)}); // thick top bar
        Technology.NodeLayer[] capacitorLayersNorm = new Technology.NodeLayer[]{capacitorLayer};
        Technology.NodeLayer[] capacitorLayersElectrolytic = new Technology.NodeLayer[]{capacitorLayer, capacitorLayerEl};
        Technology.NodeLayer[] capacitorLayersPoly2 = new Technology.NodeLayer[]{capacitorLayer, capacitorLayerPoly2};
        Technology.NodeLayer[][] capacitorLayers = {capacitorLayersNorm, capacitorLayersElectrolytic, capacitorLayersPoly2};
        PrimitiveNode.Function[] capacitorFunctions = {
            PrimitiveNode.Function.CAPAC,
            PrimitiveNode.Function.ECAPAC,
            PrimitiveNode.Function.POLY2CAPAC
        };
        capacitorNode = new MultiFunctionNode("Capacitor", 3.0, 4.0, ERectangle.fromLambda(-1.5, -2, 3, 4),
                capacitorLayersNorm, capacitorLayers, PrimitiveNode.Function.CAPAC, capacitorFunctions);
        capacitorNode.addPrimitivePorts(
                PrimitivePort.newInstance(capacitorNode, new ArcProto[]{wire_arc, bus_arc}, "a", 90, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(2), EdgeH.c(0), EdgeV.t(2)),
                PrimitivePort.newInstance(capacitorNode, new ArcProto[]{wire_arc, bus_arc}, "b", 270, 90, 1, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.b(-2), EdgeH.c(0), EdgeV.b(-2)));

        /** diode */
        Technology.NodeLayer diodeLayer1 = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-1), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.r(1), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.c(0), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(2)),
                    new Technology.TechPoint(EdgeH.c(0), BOT4BYP5),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2))});
        Technology.NodeLayer diodeLayer2 = new Technology.NodeLayer(node_lay, 0, Poly.Type.FILLED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-1), BOT4BYP5),
                    new Technology.TechPoint(EdgeH.r(1), BOT4BYP5),
                    new Technology.TechPoint(EdgeH.c(0), TOP4BYP5)});
        Technology.NodeLayer diodeLayer3 = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-1), TOP4BYP75),
                    new Technology.TechPoint(EdgeH.l(-1), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.l(-1), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.r(1), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.r(1), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.r(1), TOP4BYP25),
                    new Technology.TechPoint(EdgeH.c(0), TOP4BYP5),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(2)),
                    new Technology.TechPoint(EdgeH.c(0), BOT4BYP5),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2))});
        Technology.NodeLayer[] diodeLayersNorm = new Technology.NodeLayer[]{diodeLayer1, diodeLayer2};
        Technology.NodeLayer[] diodeLayersZener = new Technology.NodeLayer[]{diodeLayer3, diodeLayer2};
        Technology.NodeLayer[][] diodeLayers = {diodeLayersNorm, diodeLayersZener};
        PrimitiveNode.Function[] diodeFunctions = {PrimitiveNode.Function.DIODE, PrimitiveNode.Function.DIODEZ};
        diodeNode = new MultiFunctionNode("Diode", 2.0, 4.0, ERectangle.fromLambda(-1, -2, 2, 4),
                diodeLayersNorm, diodeLayers, PrimitiveNode.Function.DIODE, diodeFunctions);
        diodeNode.addPrimitivePorts(
                PrimitivePort.newInstance(diodeNode, new ArcProto[]{wire_arc}, "a", 90, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(2), EdgeH.c(0), EdgeV.t(2)),
                PrimitivePort.newInstance(diodeNode, new ArcProto[]{wire_arc}, "b", 270, 90, 1, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.b(-2), EdgeH.c(0), EdgeV.b(-2)));

        /** inductor */
        inductorNode = new ExtraBlobsNode("Inductor", 2.0, 4.0,
                new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(2)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2))}),
                new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT2BYP5, TOP4BYP33),
                    new Technology.TechPoint(EdgeH.c(0), TOP4BYP33)}),
                new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT2BYP5, EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0))}),
                new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT2BYP5, BOT4BYP33),
                    new Technology.TechPoint(EdgeH.c(0), BOT4BYP33)}));
        inductorNode.addPrimitivePorts(
                PrimitivePort.newInstance(inductorNode, new ArcProto[]{wire_arc}, "a", 90, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(2), EdgeH.c(0), EdgeV.t(2)),
                PrimitivePort.newInstance(inductorNode, new ArcProto[]{wire_arc}, "b", 270, 90, 1, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.b(-2), EdgeH.c(0), EdgeV.b(-2)));
        inductorNode.setFunction(PrimitiveNode.Function.INDUCT);

        /** meter */
        Technology.NodeLayer meterLetterV = new Technology.NodeLayer(node_lay, 0, Poly.Type.TEXTBOX, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-3), EdgeV.b(-3)),
                    new Technology.TechPoint(EdgeH.r(3), EdgeV.t(3))
                });
        meterLetterV.setMessage("V");
        meterLetterV.setDescriptor(tdBig);
        meterNode = new ExtraBlobsNode("Meter", 6.0, 6.0,
                new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.r(3), EdgeV.c(0))}),
                meterLetterV);
        meterNode.addPrimitivePorts(
                PrimitivePort.newInstance(meterNode, new ArcProto[]{wire_arc}, "a", 90, 45, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(3), EdgeH.c(0), EdgeV.t(3)),
                PrimitivePort.newInstance(meterNode, new ArcProto[]{wire_arc}, "b", 270, 45, 1, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.b(-3), EdgeH.c(0), EdgeV.b(-3)));
        meterNode.setFunction(PrimitiveNode.Function.METER);
        meterNode.setSquare();

        /** well contact */
        wellNode = new ExtraBlobsNode("Well", 4.0, 2.0,
                new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-1)),
                    new Technology.TechPoint(EdgeH.r(2), EdgeV.b(-1)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(1)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-1))}));
        wellNode.addPrimitivePorts(
                PrimitivePort.single(wellNode, new ArcProto[]{wire_arc}, "well", 90, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(1), EdgeH.c(0), EdgeV.t(1)));
        wellNode.setFunction(PrimitiveNode.Function.WELL);

        /** substrate contact */
        substrateNode = new ExtraBlobsNode("Substrate", 3.0, 3.0,
                new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.t(1.5)),
                    new Technology.TechPoint(EdgeH.l(-1.5), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.r(1.5), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.l(-1.5), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-1.5)),
                    new Technology.TechPoint(EdgeH.r(1.5), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-1.5))}));
        substrateNode.addPrimitivePorts(
                PrimitivePort.single(substrateNode, new ArcProto[]{wire_arc}, "substrate", 90, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(1.5), EdgeH.c(0), EdgeV.t(1.5)));
        substrateNode.setFunction(PrimitiveNode.Function.SUBSTRATE);

        /** two-port */
        Technology.NodeLayer twoLayerBox = new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT10BYP8, EdgeV.t(3)),
                    new Technology.TechPoint(RIGHT10BYP8, EdgeV.b(-3))});
        Technology.NodeLayer twoLayerNormWire = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-5), TOP6BYP66),
                    new Technology.TechPoint(LEFT10BYP6, TOP6BYP66),
                    new Technology.TechPoint(EdgeH.l(-5), BOT6BYP66),
                    new Technology.TechPoint(LEFT10BYP6, BOT6BYP66),
                    new Technology.TechPoint(EdgeH.r(5), TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP6, TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP6, TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP6, TOP6BYP3),
                    new Technology.TechPoint(EdgeH.r(5), BOT6BYP66),
                    new Technology.TechPoint(RIGHT10BYP6, BOT6BYP66),
                    new Technology.TechPoint(RIGHT10BYP6, BOT6BYP66),
                    new Technology.TechPoint(RIGHT10BYP6, BOT6BYP3)});
        Technology.NodeLayer twoLayerVSC = new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT10BYP6, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT10BYP6, TOP6BYP3)});
        Technology.NodeLayer twoLayerURPl = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT10BYP35, TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP45, TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP4, TOP6BYP5833),
                    new Technology.TechPoint(RIGHT10BYP4, TOP6BYP75)});
        Technology.NodeLayer twoLayerULPl = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT10BYP35, TOP6BYP66),
                    new Technology.TechPoint(LEFT10BYP45, TOP6BYP66),
                    new Technology.TechPoint(LEFT10BYP4, TOP6BYP5833),
                    new Technology.TechPoint(LEFT10BYP4, TOP6BYP75)});
        Technology.NodeLayer twoLayerCSArr = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT10BYP3833, TOP6BYP33),
                    new Technology.TechPoint(RIGHT10BYP3833, BOT6BYP33),
                    new Technology.TechPoint(RIGHT10BYP3833, BOT6BYP33),
                    new Technology.TechPoint(RIGHT10BYP33, BOT6BYP166),
                    new Technology.TechPoint(RIGHT10BYP3833, BOT6BYP33),
                    new Technology.TechPoint(RIGHT10BYP433, BOT6BYP166)});
        Technology.NodeLayer twoLayerGWire = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-5), TOP6BYP66),
                    new Technology.TechPoint(LEFT10BYP8, TOP6BYP66),
                    new Technology.TechPoint(EdgeH.l(-5), BOT6BYP66),
                    new Technology.TechPoint(LEFT10BYP8, BOT6BYP66),
                    new Technology.TechPoint(EdgeH.r(5), TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP8, TOP6BYP66),
                    new Technology.TechPoint(EdgeH.r(5), BOT6BYP66),
                    new Technology.TechPoint(RIGHT10BYP8, BOT6BYP66)});
        Technology.NodeLayer twoLayerCSWire = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT10BYP6, TOP6BYP3),
                    new Technology.TechPoint(RIGHT10BYP45, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT10BYP45, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT10BYP6, BOT6BYP3),
                    new Technology.TechPoint(RIGHT10BYP6, BOT6BYP3),
                    new Technology.TechPoint(RIGHT10BYP75, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT10BYP75, EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT10BYP6, TOP6BYP3)});
        Technology.NodeLayer twoLayerCCWire = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENEDT1, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT10BYP6, TOP6BYP66),
                    new Technology.TechPoint(LEFT10BYP6, BOT6BYP66)});
        Technology.NodeLayer twoLayerTrBox = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT10BYP8, EdgeV.t(3)),
                    new Technology.TechPoint(RIGHT10BYP8, EdgeV.t(3)),
                    new Technology.TechPoint(LEFT10BYP8, EdgeV.b(-3)),
                    new Technology.TechPoint(RIGHT10BYP8, EdgeV.b(-3))});
        Technology.NodeLayer twoLayerTr1 = new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                    new Technology.TechPoint(LEFT10BYP8, EdgeV.b(-3)),
                    new Technology.TechPoint(LEFT10BYP8, EdgeV.t(3))});
        Technology.NodeLayer twoLayerTr2 = new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT10BY1P6, EdgeV.c(0)),
                    new Technology.TechPoint(LEFT10BYP8, EdgeV.t(3)),
                    new Technology.TechPoint(LEFT10BYP8, EdgeV.b(-3))});
        Technology.NodeLayer twoLayerTr3 = new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLEARC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                    new Technology.TechPoint(RIGHT10BYP8, EdgeV.t(3)),
                    new Technology.TechPoint(RIGHT10BYP8, EdgeV.b(-3))});
        Technology.NodeLayer twoLayerTrWire = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(-5), TOP6BYP66),
                    new Technology.TechPoint(LEFT10BYP8, TOP6BYP66),
                    new Technology.TechPoint(EdgeH.l(-5), BOT6BYP66),
                    new Technology.TechPoint(LEFT10BYP8, BOT6BYP66),
                    new Technology.TechPoint(EdgeH.r(5), TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP9, TOP6BYP66),
                    new Technology.TechPoint(EdgeH.r(5), BOT6BYP66),
                    new Technology.TechPoint(RIGHT10BYP9, BOT6BYP66)});
        Technology.NodeLayer twoLayerURRPl = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(RIGHT10BYP5166, TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP6166, TOP6BYP66),
                    new Technology.TechPoint(RIGHT10BYP566, TOP6BYP5833),
                    new Technology.TechPoint(RIGHT10BYP566, TOP6BYP75)});
        Technology.NodeLayer[] twoLayersDefault = new Technology.NodeLayer[]{twoLayerBox, twoLayerGWire, twoLayerULPl, twoLayerURPl};
        Technology.NodeLayer[] twoLayersVCVS = new Technology.NodeLayer[]{twoLayerBox, twoLayerNormWire, twoLayerVSC, twoLayerULPl, twoLayerURPl};
//		Technology.NodeLayer[] twoLayersVCVS = new Technology.NodeLayer [] {twoLayerBox, twoLayerNormWire, twoLayerVSC, twoLayerURPl, twoLayerULPl};
        Technology.NodeLayer[] twoLayersVCCS = new Technology.NodeLayer[]{twoLayerBox, twoLayerNormWire, twoLayerCSWire, twoLayerCSArr, twoLayerULPl};
        Technology.NodeLayer[] twoLayersCCVS = new Technology.NodeLayer[]{twoLayerBox, twoLayerCCWire, twoLayerNormWire, twoLayerVSC, twoLayerULPl, twoLayerURPl};
//		Technology.NodeLayer[] twoLayersCCVS = new Technology.NodeLayer [] {twoLayerBox, twoLayerCCWire, twoLayerNormWire, twoLayerVSC, twoLayerURPl, twoLayerULPl};
        Technology.NodeLayer[] twoLayersCCCS = new Technology.NodeLayer[]{twoLayerBox, twoLayerCCWire, twoLayerNormWire, twoLayerCSWire, twoLayerCSArr, twoLayerULPl};
        Technology.NodeLayer[] twoLayersTran = new Technology.NodeLayer[]{twoLayerTrBox, twoLayerTr1, twoLayerTr2, twoLayerTr3, twoLayerTrWire, twoLayerULPl, twoLayerURRPl};
        Technology.NodeLayer[][] twoportLayers = {
            twoLayersVCCS, twoLayersCCVS, twoLayersVCVS, twoLayersCCCS, twoLayersTran
        };
        PrimitiveNode.Function[] twoportFunctions = {
            PrimitiveNode.Function.VCCS,
            PrimitiveNode.Function.CCVS,
            PrimitiveNode.Function.VCVS,
            PrimitiveNode.Function.CCCS,
            PrimitiveNode.Function.TLINE
        };
        twoportNode = new MultiFunctionNode("Two-Port", 10.0, 6.0, ERectangle.fromLambda(-5, -3, 10, 6),
                twoLayersDefault, twoportLayers, PrimitiveNode.Function.TLINE, twoportFunctions);
        twoportNode.addPrimitivePorts(
                PrimitivePort.newInstance(twoportNode, new ArcProto[]{wire_arc}, "a", 180, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.l(-5), TOP6BYP66, EdgeH.l(-5), TOP6BYP66),
                PrimitivePort.newInstance(twoportNode, new ArcProto[]{wire_arc}, "b", 180, 90, 1, PortCharacteristic.UNKNOWN,
                EdgeH.l(-5), BOT6BYP66, EdgeH.l(-5), BOT6BYP66),
                PrimitivePort.newInstance(twoportNode, new ArcProto[]{wire_arc}, "x", 0, 90, 2, PortCharacteristic.UNKNOWN,
                EdgeH.r(5), TOP6BYP66, EdgeH.r(5), TOP6BYP66),
                PrimitivePort.newInstance(twoportNode, new ArcProto[]{wire_arc}, "y", 0, 90, 3, PortCharacteristic.UNKNOWN,
                EdgeH.r(5), BOT6BYP66, EdgeH.r(5), BOT6BYP66));

        /** 4-port transistor */
        Technology.NodeLayer tranLayerBIP4 = new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                    new Technology.TechPoint(EdgeH.c(0), BOT4BYP25)});
        Technology.NodeLayer tranLayerPMES4 = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP5, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP35, BOT4BYP75),
                    new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP66, BOT4BYP75)});
        Technology.NodeLayer tranLayerNMES4 = new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                    new Technology.TechPoint(LEFT4BYP5, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                    new Technology.TechPoint(LEFT4BYP5, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP35, BOT4BYP5),
                    new Technology.TechPoint(LEFT4BYP5, BOT4BYP25),
                    new Technology.TechPoint(LEFT4BYP66, BOT4BYP5)});
        Technology.NodeLayer[] tran4LayersN = buildTransistorDescription(true, false, false, false, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersP = buildTransistorDescription(false, false, false, false, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersNd = buildTransistorDescription(true, true, false, false, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersPd = buildTransistorDescription(false, true, false, false, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersNnT = buildTransistorDescription(true, false, true, false, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersPnT = buildTransistorDescription(false, false, true, false, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersNfG = buildTransistorDescription(true, false, false, true, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersPfG = buildTransistorDescription(false, false, false, true, 0, 0, true, false);
        Technology.NodeLayer[] tran4LayersNCN = buildTransistorDescription(true, false, false, false, 0, 0, true, true);
        Technology.NodeLayer[] tran4LayersPCN = buildTransistorDescription(false, false, false, false, 0, 0, true, true);
        Technology.NodeLayer[] tran4LayersNvtL = buildTransistorDescription(true, false, false, false, -1, 0, true, false);
        Technology.NodeLayer[] tran4LayersPvtL = buildTransistorDescription(false, false, false, false, -1, 0, true, false);
        Technology.NodeLayer[] tran4LayersNvtH = buildTransistorDescription(true, false, false, false, 1, 0, true, false);
        Technology.NodeLayer[] tran4LayersPvtH = buildTransistorDescription(false, false, false, false, 1, 0, true, false);
        Technology.NodeLayer[] tran4LayersNht1 = buildTransistorDescription(true, false, false, false, 0, 1, true, false);
        Technology.NodeLayer[] tran4LayersPht1 = buildTransistorDescription(false, false, false, false, 0, 1, true, false);
        Technology.NodeLayer[] tran4LayersNht2 = buildTransistorDescription(true, false, false, false, 0, 2, true, false);
        Technology.NodeLayer[] tran4LayersPht2 = buildTransistorDescription(false, false, false, false, 0, 2, true, false);
        Technology.NodeLayer[] tran4LayersNht3 = buildTransistorDescription(true, false, false, false, 0, 3, true, false);
        Technology.NodeLayer[] tran4LayersPht3 = buildTransistorDescription(false, false, false, false, 0, 3, true, false);
        Technology.NodeLayer[] tran4LayersNnTht1 = buildTransistorDescription(true, false, true, false, 0, 1, true, false);
        Technology.NodeLayer[] tran4LayersPnTht1 = buildTransistorDescription(false, false, true, false, 0, 1, true, false);
        Technology.NodeLayer[] tran4LayersNnTht2 = buildTransistorDescription(true, false, true, false, 0, 2, true, false);
        Technology.NodeLayer[] tran4LayersPnTht2 = buildTransistorDescription(false, false, true, false, 0, 2, true, false);
        Technology.NodeLayer[] tran4LayersNnTht3 = buildTransistorDescription(true, false, true, false, 0, 3, true, false);
        Technology.NodeLayer[] tran4LayersPnTht3 = buildTransistorDescription(false, false, true, false, 0, 3, true, false);
        Technology.NodeLayer[] tran4LayersNPN = new Technology.NodeLayer[]{tranLayerBTran1, tranLayerTranTop, tranLayerNMOS, tranLayerBTran2, tranLayerBIP4};
        Technology.NodeLayer[] tran4LayersPNP = new Technology.NodeLayer[]{tranLayerBTran1, tranLayerTranTop, tranLayerNMOS, tranLayerBTran3, tranLayerBIP4};
        Technology.NodeLayer[] tran4LayersNJFET = new Technology.NodeLayer[]{tranLayerBTran4, tranLayerTranTop, tranLayerNMOS, tranLayerBTran5, tranLayerPMES4};
        Technology.NodeLayer[] tran4LayersPJFET = new Technology.NodeLayer[]{tranLayerBTran4, tranLayerTranTop, tranLayerNMOS, tranLayerBTran6, tranLayerNMES4};
        Technology.NodeLayer[] tran4LayersDMES = new Technology.NodeLayer[]{tranLayerBTran4, tranLayerTranTop, tranLayerNMOS, tranLayerNMES4};
        Technology.NodeLayer[] tran4LayersEMES = new Technology.NodeLayer[]{tranLayerBTran7, tranLayerNMOS, tranLayerNMES4};
        Technology.NodeLayer[][] tran4Layers = {
            tran4LayersN,
            tran4LayersNd,
            tran4LayersP,
            tran4LayersNPN,
            tran4LayersPNP,
            tran4LayersNJFET,
            tran4LayersPJFET,
            tran4LayersDMES,
            tran4LayersEMES,
            tran4LayersPd,
            tran4LayersNnT,
            tran4LayersPnT,
            tran4LayersNfG,
            tran4LayersPfG,
            tran4LayersNvtL,
            tran4LayersPvtL,
            tran4LayersNvtH,
            tran4LayersPvtH,
            tran4LayersNht1,
            tran4LayersPht1,
            tran4LayersNht2,
            tran4LayersPht2,
            tran4LayersNht3,
            tran4LayersPht3,
            tran4LayersNnTht1,
            tran4LayersPnTht1,
            tran4LayersNnTht2,
            tran4LayersPnTht2,
            tran4LayersNnTht3,
            tran4LayersPnTht3,
            tran4LayersNCN,
            tran4LayersPCN
        };
        PrimitiveNode.Function[] tran4Functions = {
            PrimitiveNode.Function.TRA4NMOS,
            PrimitiveNode.Function.TRA4DMOS,
            PrimitiveNode.Function.TRA4PMOS,
            PrimitiveNode.Function.TRA4NPN,
            PrimitiveNode.Function.TRA4PNP,
            PrimitiveNode.Function.TRA4NJFET,
            PrimitiveNode.Function.TRA4PJFET,
            PrimitiveNode.Function.TRA4DMES,
            PrimitiveNode.Function.TRA4EMES,
            PrimitiveNode.Function.TRA4PMOSD,
            PrimitiveNode.Function.TRA4NMOSNT,
            PrimitiveNode.Function.TRA4PMOSNT,
            PrimitiveNode.Function.TRA4NMOSFG,
            PrimitiveNode.Function.TRA4PMOSFG,
            PrimitiveNode.Function.TRA4NMOSVTL,
            PrimitiveNode.Function.TRA4PMOSVTL,
            PrimitiveNode.Function.TRA4NMOSVTH,
            PrimitiveNode.Function.TRA4PMOSVTH,
            PrimitiveNode.Function.TRA4NMOSHV1,
            PrimitiveNode.Function.TRA4PMOSHV1,
            PrimitiveNode.Function.TRA4NMOSHV2,
            PrimitiveNode.Function.TRA4PMOSHV2,
            PrimitiveNode.Function.TRA4NMOSHV3,
            PrimitiveNode.Function.TRA4PMOSHV3,
            PrimitiveNode.Function.TRA4NMOSNTHV1,
            PrimitiveNode.Function.TRA4PMOSNTHV1,
            PrimitiveNode.Function.TRA4NMOSNTHV2,
            PrimitiveNode.Function.TRA4PMOSNTHV2,
            PrimitiveNode.Function.TRA4NMOSNTHV3,
            PrimitiveNode.Function.TRA4PMOSNTHV3,
            PrimitiveNode.Function.TRA4NMOSCN,
            PrimitiveNode.Function.TRA4PMOSCN
        };
        transistor4Node = new MultiFunctionNode("4-Port-Transistor", 4.0, 4.0, ERectangle.fromLambda(-2, -2, 4, 3),
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.t(2)),
                        new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2)),
                        new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-2))})
                }, tran4Layers,
                PrimitiveNode.Function.TRANS4, tran4Functions);
        transistor4Node.addPrimitivePorts(
                PrimitivePort.newInstance(transistor4Node, new ArcProto[]{wire_arc}, "g", 0, 180, 0, PortCharacteristic.IN,
                EdgeH.c(0), EdgeV.t(1), EdgeH.c(0), EdgeV.t(1)),
                PrimitivePort.newInstance(transistor4Node, new ArcProto[]{wire_arc}, "s", 180, 90, 1, PortCharacteristic.BIDIR,
                EdgeH.l(-2), EdgeV.b(-2), EdgeH.l(-2), EdgeV.b(-2)),
                PrimitivePort.newInstance(transistor4Node, new ArcProto[]{wire_arc}, "d", 0, 90, 2, PortCharacteristic.BIDIR,
                EdgeH.r(2), EdgeV.b(-2), EdgeH.r(2), EdgeV.b(-2)),
                PrimitivePort.newInstance(transistor4Node, new ArcProto[]{wire_arc}, "b", 270, 90, 3, PortCharacteristic.IN,
                LEFT4BYP5, EdgeV.b(-2), LEFT4BYP5, EdgeV.b(-2)));

        /** global signal */
        globalNode = PrimitiveNode.newInstance("Global-Signal", this, 3.0, 3.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(-1.5), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.t(1.5)),
                        new Technology.TechPoint(EdgeH.r(1.5), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.b(-1.5))}),
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(LEFT3BYP9, EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), TOP3BYP9),
                        new Technology.TechPoint(RIGHT3BYP9, EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), BOT3BYP9)})
                });
        globalNode.addPrimitivePorts(
                PrimitivePort.single(globalNode, new ArcProto[]{wire_arc}, "global", 270, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.b(-1.5), EdgeH.c(0), EdgeV.b(-1.5)));
        globalNode.setFunction(PrimitiveNode.Function.CONNECT);

        /** global partition */
        Technology.NodeLayer letterGP = new Technology.NodeLayer(text_lay, 0, Poly.Type.TEXTCENT, Technology.NodeLayer.POINTS,
                Technology.TechPoint.makeCenterBox());
        letterGP.setMessage("GP");
        letterGP.setDescriptor(tdBig);
        globalPartitionNode = PrimitiveNode.newInstance("Global-Partition", this, 4.0, 2.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(0)),
                        new Technology.TechPoint(LEFT4BYP5, EdgeV.t(1)),
                        new Technology.TechPoint(RIGHT4BYP5, EdgeV.t(1)),
                        new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                        new Technology.TechPoint(RIGHT4BYP5, EdgeV.b(-1)),
                        new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-1))}),
                    letterGP
                });
        globalPartitionNode.addPrimitivePorts(
                PrimitivePort.newInstance(globalPartitionNode, new ArcProto[]{wire_arc, bus_arc}, "top", 90, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.t(1), EdgeH.c(0), EdgeV.t(1)),
                PrimitivePort.newInstance(globalPartitionNode, new ArcProto[]{wire_arc, bus_arc}, "bottom", 270, 90, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.b(-1), EdgeH.c(0), EdgeV.b(-1)));
        globalPartitionNode.setFunction(PrimitiveNode.Function.CONNECT);

        loadFactoryMenuPalette(Schematics.class.getResource("schematicMenu.xml"));

        //Foundry
        newFoundry(Foundry.Type.NONE, null);
    }

    private Technology.NodeLayer[] buildTransistorDescription(boolean nmos, boolean depletion,
            boolean nt, boolean floating, int threshold, int highVoltage, boolean fourPort, boolean carbonNanotube) {
        List<Technology.NodeLayer> layers = new ArrayList<Technology.NodeLayer>();

        // first add the base
        if (carbonNanotube) {
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-2)),
                        new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                        new Technology.TechPoint(LEFT4BYP75, BOT4BYP5),
                        new Technology.TechPoint(LEFT4BYP625, BOT4BYP5)}));
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(RIGHT4BYP625, BOT4BYP5),
                        new Technology.TechPoint(RIGHT4BYP75, BOT4BYP5),
                        new Technology.TechPoint(RIGHT4BYP75, EdgeV.b(-2)),
                        new Technology.TechPoint(EdgeH.r(2), EdgeV.b(-2))}));
            double r = 0.125 / Math.sqrt(3);
            EdgeV ringUp1 = EdgeV.by4(-0.5 + r);
            EdgeV ringUp2 = EdgeV.by4(-0.5 + 2 * r);
            EdgeV ringDown1 = EdgeV.by4(-0.5 - r);
            EdgeV ringDown2 = EdgeV.by4(-0.5 - 2 * r);
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(RIGHT4BYP625, ringUp1),
                        new Technology.TechPoint(RIGHT4BYP5, ringUp2),
                        new Technology.TechPoint(RIGHT4BYP375, ringUp1),
                        new Technology.TechPoint(RIGHT4BYP375, ringDown1),
                        new Technology.TechPoint(RIGHT4BYP5, ringDown2),
                        new Technology.TechPoint(RIGHT4BYP625, ringDown1),
                        new Technology.TechPoint(RIGHT4BYP625, ringUp1)
                    }));
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(RIGHT4BYP375, ringUp1),
                        new Technology.TechPoint(RIGHT4BYP25, ringUp2),
                        new Technology.TechPoint(RIGHT4BYP125, ringUp1),
                        new Technology.TechPoint(RIGHT4BYP125, ringDown1),
                        new Technology.TechPoint(RIGHT4BYP25, ringDown2),
                        new Technology.TechPoint(RIGHT4BYP375, ringDown1)
                    }));
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(RIGHT4BYP125, ringUp1),
                        new Technology.TechPoint(EdgeH.c(0), ringUp2),
                        new Technology.TechPoint(LEFT4BYP125, ringUp1),
                        new Technology.TechPoint(LEFT4BYP125, ringDown1),
                        new Technology.TechPoint(EdgeH.c(0), ringDown2),
                        new Technology.TechPoint(RIGHT4BYP125, ringDown1)
                    }));
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(LEFT4BYP125, ringUp1),
                        new Technology.TechPoint(LEFT4BYP25, ringUp2),
                        new Technology.TechPoint(LEFT4BYP375, ringUp1),
                        new Technology.TechPoint(LEFT4BYP375, ringDown1),
                        new Technology.TechPoint(LEFT4BYP25, ringDown2),
                        new Technology.TechPoint(LEFT4BYP125, ringDown1)
                    }));
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(LEFT4BYP375, ringUp1),
                        new Technology.TechPoint(LEFT4BYP5, ringUp2),
                        new Technology.TechPoint(LEFT4BYP625, ringUp1),
                        new Technology.TechPoint(LEFT4BYP625, ringDown1),
                        new Technology.TechPoint(LEFT4BYP5, ringDown2),
                        new Technology.TechPoint(LEFT4BYP375, ringDown1)
                    }));

//			EdgeV bubbleTop = new EdgeV(-0.25+0.0625,0);
//			layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint [] {
//				new Technology.TechPoint(EdgeH.makeCenter(), BOTBYP5),
//				new Technology.TechPoint(EdgeH.makeCenter(), bubbleTop)}));
//			layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint [] {
//				new Technology.TechPoint(LEFTBYP25, BOTBYP5),
//				new Technology.TechPoint(LEFTBYP25, bubbleTop)}));
//			layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint [] {
//				new Technology.TechPoint(LEFTBYP5, BOTBYP5),
//				new Technology.TechPoint(LEFTBYP5, bubbleTop)}));
//			layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint [] {
//				new Technology.TechPoint(RIGHTBYP25, BOTBYP5),
//				new Technology.TechPoint(RIGHTBYP25, bubbleTop)}));
//			layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint [] {
//				new Technology.TechPoint(RIGHTBYP5, BOTBYP5),
//				new Technology.TechPoint(RIGHTBYP5, bubbleTop)}));
        } else {
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-2)),
                        new Technology.TechPoint(LEFT4BYP75, EdgeV.b(-2)),
                        new Technology.TechPoint(LEFT4BYP75, BOT4BYP5),
                        new Technology.TechPoint(RIGHT4BYP75, BOT4BYP5),
                        new Technology.TechPoint(RIGHT4BYP75, EdgeV.b(-2)),
                        new Technology.TechPoint(EdgeH.r(2), EdgeV.b(-2))}));
        }
        double vertBase = -0.25;

        // if depletion, add a solid bar at the base
        if (depletion) {
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.FILLED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                        new Technology.TechPoint(LEFT4BYP75, BOT4BYP75),
                        new Technology.TechPoint(RIGHT4BYP75, BOT4BYP5)}));
        }

        // add extra horizontal line if "floating"
        if (floating) {
            EdgeV ntHeight = EdgeV.by4((vertBase + 0.0625) * 2);
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(LEFT4BYP75, ntHeight),
                        new Technology.TechPoint(RIGHT4BYP75, ntHeight)}));
        }

        // adjust space if variable threshold
        if (threshold < 0) {
            // low threshold: move closer to base
            vertBase -= 0.07;
        } else if (threshold > 0) {
            // high threshold: move farther to base
            vertBase += 0.07;
        }

        // draw gate bar if not native
        if (!nt) {
            vertBase += 0.125;
            EdgeV gateLoc = EdgeV.by4(vertBase * 2);
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(LEFT4BYP75, gateLoc),
                        new Technology.TechPoint(RIGHT4BYP75, gateLoc)}));
        }

        if (nmos) {
            // draw the stick to the gate
            EdgeV gateBot = EdgeV.by4(vertBase * 2);
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0), gateBot),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.t(1))}));
        } else {
            // draw the stick to the gate
            EdgeV bubbleBot = EdgeV.by4(vertBase * 2);
            EdgeV bubbleCtr = EdgeV.by4((vertBase + 0.125) * 2);
            EdgeV bubbleTop = EdgeV.by4((vertBase + 0.25) * 2);
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0), bubbleCtr),
                        new Technology.TechPoint(EdgeH.c(0), bubbleBot)}));
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0), bubbleTop),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.t(1))}));
        }

        // add battery if high-voltage
        if (highVoltage > 0) {
            double batteryLoc = 0.45;
            EdgeH batteryHorPos = EdgeH.by4(batteryLoc * 2);

            // height of battery from top of node
            double batteryCtr = 0.125;
            EdgeV batteryCtrEdge = EdgeV.by4(batteryCtr * 2);
            EdgeV batteryCtrShortTop = EdgeV.by4((batteryCtr + 0.05) * 2);
            EdgeV batteryCtrShortBot = EdgeV.by4((batteryCtr - 0.05) * 2);
            EdgeV batteryCtrLongTop = EdgeV.by4((batteryCtr + 0.1) * 2);
            EdgeV batteryCtrLongBot = EdgeV.by4((batteryCtr - 0.1) * 2);

            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.r(2), batteryCtrEdge),
                        new Technology.TechPoint(batteryHorPos, batteryCtrEdge)}));

            for (int i = 0; i < highVoltage; i++) {
                layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(batteryHorPos, batteryCtrLongBot),
                            new Technology.TechPoint(batteryHorPos, batteryCtrLongTop)}));
                batteryLoc -= 0.05;
                batteryHorPos = EdgeH.by4(batteryLoc * 2);

                layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(batteryHorPos, batteryCtrShortBot),
                            new Technology.TechPoint(batteryHorPos, batteryCtrShortTop)}));
                batteryLoc -= 0.05;
                batteryHorPos = EdgeH.by4(batteryLoc * 2);
            }
            batteryLoc += 0.05;
            EdgeH batteryHorEnd = EdgeH.by4(batteryLoc * 2);
            layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(batteryHorPos, batteryCtrEdge),
                        new Technology.TechPoint(batteryHorEnd, batteryCtrEdge)}));
        }

        // add base connection if requested
        if (fourPort) {
            if (nmos) {
                if (depletion || carbonNanotube) {
                    layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP75),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP75),
                                new Technology.TechPoint(LEFT4BYP4, BOT4BYP875),
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP75),
                                new Technology.TechPoint(LEFT4BYP6, BOT4BYP875)}));
                } else {
                    layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP5),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP5),
                                new Technology.TechPoint(LEFT4BYP35, BOT4BYP75),
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP5),
                                new Technology.TechPoint(LEFT4BYP66, BOT4BYP75)}));
                }
            } else {
                if (depletion || carbonNanotube) {
                    layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP75),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP4, BOT4BYP875),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP6, BOT4BYP875)}));
                } else {
                    layers.add(new Technology.NodeLayer(node_lay, 0, Poly.Type.VECTORS, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                                new Technology.TechPoint(LEFT4BYP5, BOT4BYP5),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP35, BOT4BYP75),
                                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-2)),
                                new Technology.TechPoint(LEFT4BYP66, BOT4BYP75)}));
                }
            }
        }
        Technology.NodeLayer[] descr = new Technology.NodeLayer[layers.size()];
//        EPoint fixupCorrector = EPoint.fromLambda(4, 4);
        for (int i = 0; i < layers.size(); i++) {
            descr[i] = layers.get(i);
//            descr[i].fixup(fixupCorrector);
        }
        return descr;
    }

    /**
     * Method to get the base (highlight) ERectangle associated with a NodeInst
     * in this PrimitiveNode.
     * Base ERectangle is a highlight rectangle of standard-size NodeInst of
     * this PrimtiveNode
     * By having this be a method of Technology, it can be overridden by
     * individual Technologies that need to make special considerations.
     * @param ni the NodeInst to query.
     * @return the base ERectangle of this PrimitiveNode.
     */
    @Override
    public ERectangle getNodeInstBaseRectangle(NodeInst ni) {
        NodeProto np = ni.getProto();
        if (np == andNode) {
            double width = ni.getD().size.getLambdaX() + 8;
            double height = ni.getD().size.getLambdaY() + 6;
            double unitSize = Math.min(width / 8, height / 6);
            return ERectangle.fromLambda(-0.5 * width, -0.5 * height, width - unitSize / 2, height);
        } else if (np == orNode) {
            double width = ni.getD().size.getLambdaX() + 10;
            double height = ni.getD().size.getLambdaY() + 6;
            double unitSize = Math.min(width / 10, height / 6);
            return ERectangle.fromLambda(-0.5 * width, -0.5 * height, width - unitSize / 2, height);
        } else if (np == xorNode) {
            double width = ni.getD().size.getLambdaX() + 10;
            double height = ni.getD().size.getLambdaY() + 6;
            double unitSize = Math.min(width / 10, height / 6);
            return ERectangle.fromLambda(-0.5 * width, -0.5 * height, width - unitSize / 2, height);
        }
        return super.getNodeInstBaseRectangle(ni);
    }

    /**
     * Method to convert old primitive port names to their proper PortProtos.
     * This method overrides the general Technology version and attempts Schematic-specific tests first.
     * @param portName the unknown port name, read from an old Library.
     * @param np the PrimitiveNode on which this port resides.
     * @return the proper PrimitivePort to use for this name.
     */
    @Override
    public PrimitivePort convertOldPortName(String portName, PrimitiveNode np) {
        if (np == sourceNode || np == meterNode) {
            if (portName.equals("top")) {
                return getIndexedPort(0, np);
            }
            if (portName.equals("bottom")) {
                return getIndexedPort(1, np);
            }
        }
        if (np == twoportNode) {
            if (portName.equals("upperleft")) {
                return getIndexedPort(0, np);
            }
            if (portName.equals("lowerleft")) {
                return getIndexedPort(1, np);
            }
            if (portName.equals("upperright")) {
                return getIndexedPort(2, np);
            }
            if (portName.equals("lowerright")) {
                return getIndexedPort(3, np);
            }
        }
        if (np == powerNode) {
            if (portName.equals("pwr")) {
                return getIndexedPort(0, np);
            }
        }

        return super.convertOldPortName(portName, np);
    }

    private PrimitivePort getIndexedPort(int index, PrimitiveNode np) {
        for (Iterator<PrimitivePort> it = np.getPrimitivePorts(); it.hasNext();) {
            PrimitivePort pp = it.next();
            if (index == 0) {
                return pp;
            }
            index--;
        }
        return null;
    }

    /**
     * Method to return the size of a resistor-type NodeInst in this Technology.
     * @param ni the NodeInst.
     * @param context the VarContext in which any vars will be evaluated,
     * pass in VarContext.globalContext if no context needed, or set to null
     * to avoid evaluation of variables (if any).
     * @return the size of the NodeInst.
     */
    @Override
    public PrimitiveNodeSize getResistorSize(NodeInst ni, VarContext context) {
        if (!ni.getFunction().isResistor()) {
            return null;
        }
        Object lengthObj = null;
        Variable var = ni.getVar(ATTR_LENGTH);
        if (var != null) {
            if (context != null) {
                lengthObj = context.evalVar(var, ni);
            } else {
                lengthObj = var.getObject();
            }
            double length = VarContext.objectToDouble(lengthObj, -1);
            if (length != -1) {
                lengthObj = new Double(length);
            }
        }

        Object widthObj = null;
        var = ni.getVar(ATTR_WIDTH);
        if (var != null) {
            if (context != null) {
                widthObj = context.evalVar(var, ni);
            } else {
                widthObj = var.getObject();
            }
            double width = VarContext.objectToDouble(widthObj, -1);
            if (width != -1) {
                widthObj = new Double(width);
            }
        }
        PrimitiveNodeSize size = new PrimitiveNodeSize(widthObj, lengthObj, true);
        return size;
    }

    /**
     * Method to return the size of a transistor NodeInst in this Technology.
     * You should most likely be calling NodeInst.getTransistorSize instead of this.
     * @param ni the NodeInst.
     * @param context the VarContext, set to VarContext.globalContext if not needed.
     * set to Null to avoid evaluation of variable.
     * @return the size of the NodeInst.
     * For FET transistors, the width of the Dimension is the width of the transistor
     * and the height of the Dimension is the length of the transistor.
     * For non-FET transistors, the width of the dimension is the area of the transistor.
     */
    @Override
    public TransistorSize getTransistorSize(NodeInst ni, VarContext context) {
        if (ni.getFunction().isFET()) {
            Object lengthObj = null;
            Variable var = ni.getVar(ATTR_LENGTH);
            if (var != null) {
                if (context != null) {
                    lengthObj = context.evalVar(var, ni);
                } else {
                    lengthObj = var.getObject();
                }
                double length = VarContext.objectToDouble(lengthObj, -1);
                if (length != -1) {
                    lengthObj = new Double(length);
                }
            }

            Object widthObj = null;
            var = ni.getVar(ATTR_WIDTH);
            if (var != null) {
                if (context != null) {
                    widthObj = context.evalVar(var, ni);
                } else {
                    widthObj = var.getObject();
                }
                double width = VarContext.objectToDouble(widthObj, -1);
                if (width != -1) {
                    widthObj = new Double(width);
                }
            }


            Object mFactorObj = null;
            var = ni.getVar(SimulationTool.M_FACTOR_KEY);
            if (var != null) {
                if (context != null) {
                    mFactorObj = context.evalVar(var, ni);
                } else {
                    mFactorObj = var.getObject();
                }
                double mFactor = VarContext.objectToDouble(mFactorObj, -1);
                if (mFactor != -1) {
                    mFactorObj = new Double(mFactor);
                }
            }

            TransistorSize size = new TransistorSize(widthObj, lengthObj, new Double(1.0), mFactorObj, true);
            return size;
        }
        Object areaObj = new Double(0);
        if (context != null) {
            areaObj = context.evalVar(ni.getVar(ATTR_AREA));
            double area = VarContext.objectToDouble(areaObj, -1);
            if (area != -1) {
                areaObj = new Double(area);
            }
        }
        TransistorSize size = new TransistorSize(areaObj, new Double(1.0), new Double(1.0), null, true);
        return size;
    }

    /**
     * Method to set the size of a transistor NodeInst in this technology.
     * You should be calling NodeInst.setTransistorSize instead of this.
     * Width may be the area for non-FET transistors, in which case length is ignored.
     * You may also want to call setTransistorSize(NodeInst, Object, Object) to
     * set the variables to a non-double value (such as a String).
     * @param ni the NodeInst
     * @param width the new width
     * @param length the new length
     * @param ep EditingPreferences with default TextDescriptors
     */
    @Override
    public void setPrimitiveNodeSize(NodeInst ni, double width, double length, EditingPreferences ep) {
        setPrimitiveNodeSize(ni, new Double(width), new Double(length), ep);
//        if (ni.isFET())
//        {
//            Variable var = ni.getVar(ATTR_LENGTH);
//            if (var == null) {
//                var = ni.newVar(ATTR_LENGTH, new Double(length));
//            } else {
//                var = ni.updateVar(var.getKey(), new Double(length));
//            }
//            if (var != null) var.setDisplay(true);
//
//            var = ni.getVar(ATTR_WIDTH);
//            if (var == null) {
//                var = ni.newVar(ATTR_WIDTH, new Double(width));
//            } else {
//                var = ni.updateVar(var.getKey(), new Double(width));
//            }
//            if (var != null) var.setDisplay(true);
//        } else {
//            Variable var = ni.getVar(ATTR_AREA);
//            if (var != null) {
//                var = ni.updateVar(var.getKey(), new Double(width));
//            }
//            if (var != null) var.setDisplay(true);
//        }
    }

    /**
     * Method to set the size of a transistor NodeInst in this technology.
     * You should be calling NodeInst.setTransistorSize(Object, Object) instead.
     * Width may be the area for non-FET transistors, in which case length is ignored.
     * @param ni the NodeInst
     * @param width the new width
     * @param length the new length
     * @param ep EditingPreferences with default TextDescriptors
     */
    public void setPrimitiveNodeSize(NodeInst ni, Object width, Object length, EditingPreferences ep) {
        if (ni.getFunction().isFET() || ni.getFunction().isResistor()) {
            Variable var = ni.getVar(ATTR_LENGTH);
            if (var == null) {
                ni.newDisplayVar(ATTR_LENGTH, length, ep);
            } else {
                ni.addVar(var.withObject(length).withDisplay(true));
            }
//            if (var == null) {
//                var = ni.newVar(ATTR_LENGTH, length);
//            } else {
//                var = ni.updateVar(var.getKey(), length);
//            }
//            if (var != null) var.setDisplay(true);

            var = ni.getVar(ATTR_WIDTH);
            if (var == null) {
                ni.newDisplayVar(ATTR_WIDTH, width, ep);
            } else {
                ni.addVar(var.withObject(width).withDisplay(true));
            }
//            if (var == null) {
//                var = ni.newVar(ATTR_WIDTH, width);
//            } else {
//                var = ni.updateVar(var.getKey(), width);
//            }
//            if (var != null) var.setDisplay(true);
        } else {
            Variable var = ni.getVar(ATTR_AREA);
            if (var != null) {
                ni.addVar(var.withObject(width).withDisplay(true));
            }
//            if (var != null) {
//                var = ni.updateVar(var.getKey(), width);
//            }
//            if (var != null) var.setDisplay(true);
        }
    }

    /****************************** GENERAL PREFERENCES ******************************/
    /**
     * Method to determine the default schematic technology.
     * This is the technology to really use when the current technology is "schematics" and you want a layout technology.
     * This is important in Spice deck generation (for example) because the Spice primitives may
     * say "2x3" on them, but a real technology (such as "mocmos") must be found to convert these pure
     * numbers to real spacings for the deck.
     */
    public static Technology getDefaultSchematicTechnology() {
        // see if the default schematics technology is already set
        Technology schemTech = User.getSchematicTechnology();
        if (schemTech != null) {
            return schemTech;
        }

        // look at all circuitry and see which technologies are in use
        Map<Technology, MutableInteger> usedTechnologies = new HashMap<Technology, MutableInteger>();
        for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext();) {
            usedTechnologies.put(it.next(), new MutableInteger(0));
        }
        for (Iterator<Library> lIt = Library.getLibraries(); lIt.hasNext();) {
            Library lib = lIt.next();
            if (lib.isHidden()) {
                continue;
            }
            for (Iterator<Cell> cIt = lib.getCells(); cIt.hasNext();) {
                Cell cell = cIt.next();
                Technology tech = cell.getTechnology();
                if (tech == null) {
                    continue;
                }
                MutableInteger mi = usedTechnologies.get(tech);
                mi.increment();
            }
        }

        // ignore nonlayout technologies
        for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext();) {
            Technology tech = it.next();
            MutableInteger mi = usedTechnologies.get(tech);
            if (!tech.isLayout()
                    || tech.isNonElectrical() || tech.isNoPrimitiveNodes()) {
                mi.setValue(-1);
            }
        }

        // figure out the most popular technology
        int bestAmount = -1;
        Technology bestTech = null;
        for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext();) {
            Technology tech = it.next();
            MutableInteger mi = usedTechnologies.get(tech);
            if (mi.intValue() <= bestAmount) {
                continue;
            }
            bestAmount = mi.intValue();
            bestTech = tech;
        }
        if (bestTech == null) {
            // presume mosis cmos
            bestTech = getMocmosTechnology();
        }

//		User.setSchematicTechnology(bestTech);
        return bestTech;
    }

    /**
     * Method to return a gate PortInst for this transistor NodeInst.
     * Implementation Note: May want to make this a more general
     * method, getPrimitivePort(PortType), if the number of port
     * types increases.  Note: You should be calling
     * NodeInst.getTransistorDrainPort() instead of this, most likely.
     * @param ni the NodeInst
     * @return a PortInst for the gate of the transistor
     */
    @Override
    public PortInst getTransistorDrainPort(NodeInst ni) {
        return ni.getPortInst(2);
    }

    /** Return a substrate PortInst for this transistor NodeInst
     * @param ni the NodeInst
     * @return a PortInst for the substrate contact of the transistor
     */
    @Override
    public PortInst getTransistorBiasPort(NodeInst ni) {
        if (ni.getNumPortInsts() < 4) {
            return null;
        }
        return ni.getPortInst(3);
    }

    /**
     * Method to tell the size of negating bubbles.
     * The default is 1.2 .
     * @return the size of negating bubbles (the diameter).
     */
    public double getNegatingBubbleSize() {
        return 1.2;
    }

    /**
     * Method to tell the VHDL names for a primitive in this technology, by default.
     * These names have the form REGULAR/NEGATED, where REGULAR is the name to use
     * for regular uses of the primitive, and NEGATED is the name to use for negated uses.
     * @param np the primitive to query.
     * @return the the VHDL names for the primitive, by default.
     */
    public String getFactoryVHDLNames(PrimitiveNode np) {
        if (np == bufferNode) {
            return "buffer/inverter";
        }
        if (np == andNode) {
            return "and/nand";
        }
        if (np == orNode) {
            return "or/nor";
        }
        if (np == xorNode) {
            return "xor/xnor";
        }
        if (np == muxNode) {
            return "mux";
        }
        return "";
    }

    private class SquareSizablePort extends PrimitivePort {

        private SquareSizablePort(PrimitiveNode parent, ArcProto[] portArcs, String protoName,
                int portAngle, int portRange, int portTopology, PortCharacteristic characteristic,
                EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
            super(parent, portArcs, protoName, false, portAngle, portRange, portTopology, characteristic, false, true,
                    left, bottom, right, top);
            assert left.getMultiplier() == 0 && right.getMultiplier() == 0 && bottom.getMultiplier() == 0 && top.getMultiplier() == 0;
        }

        @Override
        protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            PrimitiveNode pn = getParent();
            ERectangle full = pn.getFullRectangle();
            double lambda = Math.min(n.size.getLambdaX() / full.getLambdaWidth(), n.size.getLambdaY() / full.getLambdaHeight());

            if (lambda != 0) {
                lambda += 1;
                // standard port computation
                double portLowX = getLeft().getAdder().getFixp() * lambda;
                double portHighX = getRight().getAdder().getFixp() * lambda;
                double portLowY = getBottom().getAdder().getFixp() * lambda;
                double portHighY = getTop().getAdder().getFixp() * lambda;
                b.pushPoint(portLowX, portLowY);
                b.pushPoint(portHighX, portLowY);
                b.pushPoint(portHighX, portHighY);
                b.pushPoint(portLowX, portHighY);
                b.pushPoly(Poly.Type.FILLED, null, null, null);
                return;
            }

            // special selection did not apply: do normal port computation
            super.genShape(b, n);
        }
    }

    private class SpecialSelectionPort extends PrimitivePort {

        private SpecialSelectionPort(PrimitiveNode parent, String protoName,
                EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
            super(parent, new ArcProto[]{wire_arc, bus_arc}, protoName, false, 180, 45, 0, PortCharacteristic.IN, true, true,
                    left, bottom, right, top);
        }

        private SpecialSelectionPort(PrimitiveNode parent, String protoName,
                int portAngle, int portRange, PortCharacteristic characteristic, boolean negatable,
                EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
            super(parent, new ArcProto[]{wire_arc, bus_arc}, protoName, false, portAngle, portRange, 0, characteristic, true, negatable,
                    left, bottom, right, top);
        }

        @Override
        protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            PrimitiveNode pn = getParent();
            // determine the grid size
            if (pn == andNode || pn == orNode || pn == xorNode) {
                ERectangle full = pn.getFullRectangle();
                double lambda = Math.min(n.size.getLambdaX() / full.getLambdaWidth(), n.size.getLambdaY() / full.getLambdaHeight());
                if (lambda != 0) {
                    lambda += 1;
                    // standard port computation
                    double portLowX = getLeft().getAdder().getFixp() * lambda;
                    double portHighX = getRight().getAdder().getFixp() * lambda;
                    double portLowY = getBottom().getMultiplier() != 0 ? getBottom().getFixpValue(n.size) : getBottom().getAdder().getFixp() * lambda;
                    double portHighY = getTop().getMultiplier() != 0 ? getTop().getFixpValue(n.size) : getTop().getAdder().getFixp() * lambda;
                    b.pushPoint(portLowX, portLowY);
                    b.pushPoint(portHighX, portLowY);
                    b.pushPoint(portHighX, portHighY);
                    b.pushPoint(portLowX, portHighY);
                    b.pushPoly(Poly.Type.FILLED, null, null, null);
                    return;
                }
            }

            // special selection did not apply: do normal port computation
            super.genShape(b, n);
        }

        @Override
        protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n, Point2D selectPt) {
            PrimitiveNode pn = getParent();
            // determine the grid size
            double lambda = 0;
            if (pn == andNode || pn == orNode || pn == xorNode) {
                ERectangle full = pn.getFullRectangle();
                lambda = Math.min(n.size.getLambdaX() / full.getLambdaWidth(), n.size.getLambdaY() / full.getLambdaHeight());
            }
            lambda += 1;

            // initialize
            double wantX = selectPt.getX();
            double wantY = selectPt.getY();
            double bestDist = Double.MAX_VALUE;
            double bestX = 0, bestY = 0;

            // determine total number of arcs already on this port
            int total = 0;
            BitSet headEnds = new BitSet();
            List<ImmutableArcInst> connArcs = b.getConnections(headEnds, n, getId());
            total = connArcs.size();

            // cycle through the arc positions
            total = Math.max(total + 2, 3);
            for (int i = 0; i < total; i++) {
                // compute the position along the left edge
                double yPosition = (i + 1) / 2 * 2;
                if ((i & 1) != 0) {
                    yPosition = -yPosition;
                }

                // compute indentation
                double xPosition = -4;
                if (pn == switchNode) {
                    xPosition = -2;
                } else if (pn == muxNode) {
                    xPosition = -(8.0 + n.size.getLambdaX()) * 4 / 10;
                } else if (pn == orNode || pn == xorNode) {
                    switch (i) {
                        case 0:
                            xPosition += 0.75;
                            break;
                        case 1:
                        case 2:
                            xPosition += 0.5;
                            break;
                    }
                }

                // fill the polygon with that point
                Point2D.Double pt = new Point2D.Double(xPosition * lambda, yPosition * lambda);
                Point2D.Double pt1 = new Point2D.Double();
                n.orient.pureRotate().transform(pt, pt1);
                pt1.setLocation(n.anchor.getLambdaX() + pt1.getX(), n.anchor.getLambdaY() + pt1.getY());

                // check for duplication
                boolean found = false;
                for (int j = 0; j < connArcs.size(); j++) {
                    ImmutableArcInst a = connArcs.get(j);
                    boolean isHead = headEnds.get(j);
                    EPoint connLocation = isHead ? a.headLocation : a.tailLocation;
                    if (connLocation.equals(pt1)) {
                        found = true;
                        break;
                    }
                }

                // if there is no duplication, this is a possible position
                if (!found) {
                    double dist = Math.abs(wantX - pt1.getX()) + Math.abs(wantY - pt1.getY());
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestX = pt.getX();
                        bestY = pt.getY();   //bestIndex = i;
                    }
                }
            }
            if (bestDist == Double.MAX_VALUE) {
                System.out.println("Warning: cannot find gate port");
            }

            // set the closest port
            b.pushPoint(bestX * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE), bestY * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE));
            b.pushPoly(Poly.Type.FILLED, null, null, null);
        }
    }

    private class WirePinNode extends PrimitiveNode {

        private WirePinNode() {
            super("Wire_Pin", Schematics.this, EPoint.fromLambda(0.25, 0.25), 0.5, 0.5, ERectangle.fromLambda(-0.25, -0.25, 0.5, 0.5),
                    new Technology.NodeLayer[]{
                        new Technology.NodeLayer(arc_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.r(0.25), EdgeV.c(0))})
                    });
            addPrimitivePorts(
                    PrimitivePort.single(this, new ArcProto[]{wire_arc}, "wire", 0, 180, 0, PortCharacteristic.UNKNOWN,
                    EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0)));
            setFunction(PrimitiveNode.Function.PIN);
            setSquare();
            setWipeOn1or2();
            setCanBeZeroSize();
        }

        /**
         * Puts into shape builder s the polygons that describe node "n", given a set of
         * NodeLayer objects to use.
         * This method is overridden by specific Technologies.
         * @param b shape builder where to put polygons
         * @param n the ImmutableNodeInst that is being described.
         */
        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            assert n.protoId == getId();
            // if node is erased, remove layers
            if (b.pinUseCount(n)) {
                return;
            }

            Technology.NodeLayer[] primLayers = getNodeLayers();
            b.genShapeOfNode(n, this, primLayers, null);
        }
    }

    private class BusPinNode extends PrimitiveNode {

        private final EdgeV maxRadius = EdgeV.by2(0.5 * 2);

        private BusPinNode() {
            super("Bus_Pin", Schematics.this, EPoint.fromLambda(1, 1), 2, 2, ERectangle.fromLambda(-1, -1, 2, 2),
                    new Technology.NodeLayer[]{
                        new Technology.NodeLayer(bus_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.r(1), EdgeV.c(0))}),
                        new Technology.NodeLayer(arc_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.r(1), EdgeV.c(0))})
                    });
            addPrimitivePorts(
                    PrimitivePort.single(this, new ArcProto[]{wire_arc, bus_arc}, "bus", 0, 180, 0, PortCharacteristic.UNKNOWN,
                    EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0)));
            setFunction(PrimitiveNode.Function.PIN);
            setSquare();
            setCanBeZeroSize();
        }

        /**
         * Puts into shape builder s the polygons that describe node "n", given a set of
         * NodeLayer objects to use.
         * This method is overridden by specific Technologies.
         * @param b shape builder where to put polygons
         * @param n the ImmutableNodeInst that is being described.
         */
        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            assert n.protoId == getId();
            // bus pins get bigger in "T" configurations, disappear when alone and exported
            int busCon = 0, nonBusCon = 0;
            if (b.getCellBackup() != null) {
                for (ImmutableArcInst a : b.getConnections(null, n)) {
                    if (a.protoId == bus_arc.getId()) {
                        busCon++;
                    } else {
                        nonBusCon++;
                    }
                }
            } else {
                busCon = 3;
            }
            int implicitCon = 0;
            if (busCon == 0 && nonBusCon == 0) {
                implicitCon = 1;
            }

//			// if the next level up the hierarchy is visible, consider arcs connected there
//			if (context != null && m.hasExportsOnNode(n))
//			{
//				Nodable no = context.getNodable();
//				if (no != null && no instanceof NodeInst)
//				{
//					NodeInst upni = (NodeInst)no;
//					if (upni.getProto() == ni.getParent() && wnd != null /*&& upni.getParent() == wnd.getCell()*/)
//					{
//						for(Iterator<Export> it = ni.getExports(); it.hasNext(); )
//						{
//							Export pp = it.next();
//							for(Iterator<Connection> pIt = upni.getConnections(); pIt.hasNext(); )
//							{
//								Connection con = pIt.next();
//								if (con.getPortInst().getPortProto() != pp) continue;
//								if (con.getArc().getProto() == bus_arc) busCon++; else
//									nonBusCon++;
//							}
//						}
//					}
//				}
//			}

            // bus pins don't show wire pin in center if not tapped
            double wireDiscSize = 0.125;
            if (nonBusCon == 0) {
                wireDiscSize = 0;
            }

            double busDiscSize;
            if (busCon + implicitCon > 2) {
                // larger pin because it is connected to 3 or more bus arcs
                busDiscSize = 0.5;
            } else {
                // smaller pin because it has 0, 1, or 2 connections
                busDiscSize = 0.25;
                if (busCon == 0) {
                    if (nonBusCon + implicitCon > 2) {
                        busDiscSize = 0;
                    } else {
                        if (b.hasExportsOnNode(n)) {
                            wireDiscSize = busDiscSize = 0;
                        }
                    }
                }
            }
            int totalLayers = 0;
            if (busDiscSize > 0) {
                totalLayers++;
            }
            if (wireDiscSize >= 0) {
                totalLayers++;
            }
            Technology.NodeLayer[] busPinLayers = new Technology.NodeLayer[totalLayers];
            totalLayers = 0;
            if (busDiscSize > 0) {
                busPinLayers[totalLayers++] = new Technology.NodeLayer(bus_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.c(0), EdgeV.by2(busDiscSize * 2))});
            }
            if (wireDiscSize >= 0) {
                busPinLayers[totalLayers++] = new Technology.NodeLayer(arc_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.c(0), EdgeV.by2(wireDiscSize * 2))});
            }
            b.genShapeOfNode(n, this, busPinLayers, null);
        }

        @Override
        public void genBounds(ImmutableNodeInst n, long[] gridCoords) {
            long fixpRadius = Math.max(0, maxRadius.getFixpValue(n.size));
            long gridRadius = -((-fixpRadius) >> FixpCoord.FRACTION_BITS);
            long anchorX = n.anchor.getGridX();
            long anchorY = n.anchor.getGridY();
            gridCoords[0] = anchorX - gridRadius;
            gridCoords[1] = anchorY - gridRadius;
            gridCoords[2] = anchorX + gridRadius;
            gridCoords[3] = anchorY + gridRadius;
        }

        @Override
        public void genElibBounds(CellBackup cellBackup, ImmutableNodeInst n, long[] gridCoords) {
            BoundsBuilder b = cellBackup != null ? new BoundsBuilder(cellBackup) : new BoundsBuilder((TechPool) null);
            genShape(b, n);
            ERectangle bounds = b.makeBounds();
            gridCoords[0] = bounds.getGridMinX();
            gridCoords[1] = bounds.getGridMinY();
            gridCoords[2] = bounds.getGridMaxX();
            gridCoords[3] = bounds.getGridMaxY();
        }
    }

    private class AndOrXorNode extends PrimitiveNode {

        private AndOrXorNode(String protoName, double width, double height,
                ERectangle baseRectangle, Technology.NodeLayer[] layers) {
            super(protoName, Schematics.this, EPoint.fromLambda(0.5 * width, 0.5 * height),
                    width, height, baseRectangle, layers);
        }

        /**
         * Puts into shape builder s the polygons that describe node "n", given a set of
         * NodeLayer objects to use.
         * This method is overridden by specific Technologies.
         * @param b shape builder where to put polygons
         * @param n the ImmutableNodeInst that is being described.
         */
        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            assert n.protoId == getId();
            Technology.NodeLayer[] primLayers = getNodeLayers();
            ERectangle full = getFullRectangle();
            double lambda = Math.min(n.size.getLambdaX() / full.getLambdaWidth(), n.size.getLambdaY() / full.getLambdaHeight());
            if (lambda != 0) {
                lambda += 1;
                Technology.NodeLayer[] newPrimLayers = new Technology.NodeLayer[primLayers.length];
                for (int i = 0; i < primLayers.length; i++) {
                    Technology.NodeLayer nl = new Technology.NodeLayer(primLayers[i]);
                    Technology.TechPoint[] points = nl.getPoints();
                    for (int j = 0; j < points.length; j++) {
                        EdgeH h = points[j].getX();
                        EdgeV v = points[j].getY();
                        if (h.getMultiplier() == 0) {
                            h = EdgeH.c(h.getAdder().getLambda() * lambda);
                        }
                        if (v.getMultiplier() == 0) {
                            v = EdgeV.c(v.getAdder().getLambda() * lambda);
                        }
                        points[j] = new Technology.TechPoint(h, v);
                    }
                    newPrimLayers[i] = nl;
                }
                primLayers = newPrimLayers;
            }
            b.genShapeOfNode(n, this, primLayers, null);
        }
    }

    private class SwitchNode extends PrimitiveNode {

        private SwitchNode() {
            super("Switch", Schematics.this, EPoint.fromLambda(3, 1), 6, 2, ERectangle.fromLambda(-2.5, -0.5, 5, 1),
                    new Technology.NodeLayer[]{
                        new Technology.NodeLayer(node_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.r(1.75), EdgeV.c(0))}),
                        new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(0))}),
                        new Technology.NodeLayer(node_lay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                            new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(1))})
                    });
            PrimitivePort switch_port = new SpecialSelectionPort(this, "a",
                    180, 90, PortCharacteristic.UNKNOWN, false,
                    EdgeH.l(-2), EdgeV.b(0), EdgeH.l(-2), EdgeV.t(0));
            addPrimitivePorts(
                    switch_port,
                    PrimitivePort.newInstance(this, new ArcProto[]{wire_arc, bus_arc}, "y", 0, 90, 1, PortCharacteristic.UNKNOWN,
                    EdgeH.r(2), EdgeV.c(0), EdgeH.r(2), EdgeV.c(0)));
            setFunction(PrimitiveNode.Function.UNKNOWN);
            setAutoGrowth(0, 4);
        }

        /**
         * Puts into shape builder s the polygons that describe node "n", given a set of
         * NodeLayer objects to use.
         * This method is overridden by specific Technologies.
         * @param b shape builder where to put polygons
         * @param n the ImmutableNodeInst that is being described.
         */
        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            assert n.protoId == getId();
            Technology.NodeLayer[] primLayers = getNodeLayers();
            int numLayers = 3;
            if (n.size.getLambdaY() >= 2) {
                numLayers += ((int) n.size.getLambdaY() / 2);
            }
            Technology.NodeLayer[] switchLayers = new Technology.NodeLayer[numLayers];
            switchLayers[0] = primLayers[0];
            if ((numLayers % 2) == 0) {
                switchLayers[1] = primLayers[1];
            } else {
                switchLayers[1] = primLayers[2];
            }
            for (int i = 2; i < numLayers; i++) {
                double yValue = 2 * (i - 1) - 2;
                switchLayers[i] = new Technology.NodeLayer(node_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(yValue)),
                            new Technology.TechPoint(EdgeH.l(-1.75), EdgeV.b(yValue))});
            }
            b.genShapeOfNode(n, this, switchLayers, null);
        }
    }

    private class ExtraBlobsNode extends PrimitiveNode {

        private ExtraBlobsNode(String protoName, double defWidth, double defHeight, Technology.NodeLayer... layers) {
            super(protoName, Schematics.this, EPoint.fromLambda(0.5 * defWidth, 0.5 * defHeight), defWidth, defHeight,
                    ERectangle.fromLambda(-0.5 * defWidth, -0.5 * defHeight, defWidth, defHeight), layers);
        }

        private ExtraBlobsNode(String protoName, EPoint fullSizeCorrector,
                double defWidth, double defHeight, ERectangle baseRectangle, Technology.NodeLayer[] layers) {
            super(protoName, Schematics.this, fullSizeCorrector, defWidth, defHeight, baseRectangle, layers);
        }

        /**
         * Puts into shape builder s the polygons that describe node "n", given a set of
         * NodeLayer objects to use.
         * This method is overridden by specific Technologies.
         * @param b shape builder where to put polygons
         * @param n the ImmutableNodeInst that is being described.
         */
        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            assert n.protoId == getId();
            Technology.NodeLayer[] primLayers = getNodeLayers();
            genShapeOfNode(b, n, primLayers);
        }

        void genShapeOfNode(AbstractShapeBuilder b, ImmutableNodeInst n, Technology.NodeLayer[] primLayers) {
            // make a list of extra blobs that need to be drawn
            List<PrimitivePort> extraBlobList = null;
            BitSet headEnds = new BitSet();
            List<ImmutableArcInst> connArcs = b.getConnections(headEnds, n);
            PortProtoId prevPortId = null;
            int arcsCount = 0;
            for (int i = 0; i < connArcs.size(); i++) {
                ImmutableArcInst a = connArcs.get(i);
                PortProtoId portId = headEnds.get(i) ? a.headPortId : a.tailPortId;
                assert portId.parentId == n.protoId;
                if (portId == prevPortId) {
                    arcsCount++;
                    if (arcsCount == 2) {
                        if (extraBlobList == null) {
                            extraBlobList = new ArrayList<PrimitivePort>();
                        }
                        extraBlobList.add(getPort(portId));
                    }
                } else {
                    prevPortId = portId;
                    arcsCount = 1;
                }
            }
            if (extraBlobList != null) {
                // must add extra blobs to this node
                double blobSize = wirePinNode.getFactoryDefaultBaseDimension().getLambdaWidth() / 2;
//				double blobSize = wirePinNode.getDefWidth() / 2;
                Technology.NodeLayer[] blobLayers = new Technology.NodeLayer[primLayers.length + extraBlobList.size()];
                int fill = 0;
                for (int i = 0; i < primLayers.length; i++) {
                    blobLayers[fill++] = primLayers[i];
                }
                for (PrimitivePort pp : extraBlobList) {
                    EdgeH xEdge = new EdgeH(pp.getLeft().getMultiplier(), pp.getLeft().getAdder().getLambda() + blobSize);
                    blobLayers[fill++] = new Technology.NodeLayer(arc_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                                new Technology.TechPoint(pp.getLeft(), pp.getTop()),
                                new Technology.TechPoint(xEdge, pp.getTop())});
                }
                primLayers = blobLayers;
            }
            b.genShapeOfNode(n, this, primLayers, null);
        }
    }

    private class MultiFunctionNode extends ExtraBlobsNode {

        private final Technology.NodeLayer[][] altNodeLayers;
        private final Function[] altFunctions;
        private final EnumMap<PrimitiveNode.Function, Integer> functionBits = new EnumMap<PrimitiveNode.Function, Integer>(PrimitiveNode.Function.class);

        private MultiFunctionNode(String protoName,
                double defWidth, double defHeight, ERectangle baseRectangle, Technology.NodeLayer[] layers,
                Technology.NodeLayer[][] altNodeLayers, Function function, Function[] altFunctions) {
            super(protoName, EPoint.fromLambda(0.5 * defWidth, 0.5 * defHeight), defWidth, defHeight, baseRectangle, layers);
            // check the arguments
            assert altNodeLayers.length == altFunctions.length;
            this.altNodeLayers = altNodeLayers;
            this.altFunctions = altFunctions;
            for (int techBits = 0; techBits < altFunctions.length; techBits++) {
                Integer old = functionBits.put(altFunctions[techBits], Integer.valueOf(techBits));
                assert old == null;
            }
            setFunction(function);
            checkNodeLayers(altNodeLayers);
        }

        /**
         * Puts into shape builder s the polygons that describe node "n", given a set of
         * NodeLayer objects to use.
         * This method is overridden by specific Technologies.
         * @param b shape builder where to put polygons
         * @param n the ImmutableNodeInst that is being described.
         */
        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            assert n.protoId == getId();
            Technology.NodeLayer[] primLayers;
            int techBits = n.techBits;
            if (techBits >= 0 && techBits < altNodeLayers.length) {
                primLayers = altNodeLayers[techBits];
            } else {
                primLayers = getNodeLayers();
            }
            genShapeOfNode(b, n, primLayers);
        }

        /**
         * Method to return the pure "NodeProto Function" a PrimitiveNode in this Technology.
         * This method is overridden by technologies (such as Schematics) that know the node's function.
         * @param techBits tech bits
         * @return the PrimitiveNode.Function that describes the PrinitiveNode with specific tech bits.
         */
        @Override
        public PrimitiveNode.Function getPrimitiveFunction(int techBits) {
            return techBits >= 0 && techBits < altFunctions.length ? altFunctions[techBits] : getFunction();
        }

        /**
         * Method to return the technology-specific function bits for a given PrimitiveNode.Function.
         * @param function the universal function description
         * @return the technology-specific bits to use for that function in this technology.
         */
        @Override
        public int getPrimitiveFunctionBits(PrimitiveNode.Function function) {
            Integer techBits = functionBits.get(function);
            return techBits != null ? techBits.intValue() : 0;
        }

        /**
         * Method to tell whether this primitive node prototype has technology-specific information on it.
         * At the current time, only certain Schematics primitives have this information.
         * @return true this primitive node prototype has technology-specific information on it.
         */
        @Override
        public boolean isTechSpecific() {
            return true;
        }

        @Override
        protected void dump(PrintWriter out) {
            out.print("MultiFunction ");
            super.dump(out);
            for (int techBits = 0; techBits < altNodeLayers.length; techBits++) {
                out.println("\ttechBits=" + techBits + " " + altFunctions[techBits]);
                dumpNodeLayers(out, altNodeLayers[techBits], false);
            }
        }

        @Override
        protected Xml.PrimitiveNodeGroup makeXml(Map<Object, Map<String, Object>> additionalAttributes) {
            List<Technology.NodeLayer> allPrimLayers = new ArrayList<Technology.NodeLayer>(Arrays.asList(getNodeLayers()));
            assert getElectricalLayers() == null;
            Xml.PrimitiveNodeGroup ng = super.makeXml(additionalAttributes);
            ng.isSingleton = false;
            assert ng.nodeLayers.size() == allPrimLayers.size();
            for (Xml.NodeLayer nld : ng.nodeLayers) {
                nld.inNodes = new BitSet();
                nld.inNodes.set(0);
            }
            for (int techBits = 0; techBits < altNodeLayers.length; techBits++) {
                Function f = altFunctions[techBits];
                Xml.PrimitiveNode n = new Xml.PrimitiveNode();
                ng.nodes.add(n);
                n.name = getName() + "-" + f.getName();
                n.function = f;
                Technology.NodeLayer[] primLayers = altNodeLayers[techBits];
                int oldIx = -1;
                int nli = 0;
                while (nli < primLayers.length) {
                    int i = nli;
                    int ix = -1;
                    while (i < primLayers.length) {
                        ix = allPrimLayers.indexOf(primLayers[i]);
                        if (ix >= 0) {
                            break;
                        }
                        i++;
                    }
                    if (ix < 0) {
                        ix = allPrimLayers.size();
                    }
                    assert oldIx < ix;
                    for (int j = i - 1; j >= nli; j--) {
                        Technology.NodeLayer nl = primLayers[j];
                        Xml.NodeLayer nld = nl.makeXml(false, true, true, additionalAttributes);
                        nld.inNodes = new BitSet();
                        nld.inNodes.set(techBits + 1);
                        allPrimLayers.add(ix, nl);
                        ng.nodeLayers.add(ix, nld);
                    }
                    oldIx = ix + i - nli;
                    if (i < primLayers.length) {
                        assert allPrimLayers.get(oldIx) == primLayers[i];
                        ng.nodeLayers.get(oldIx).inNodes.set(techBits + 1);
                    }
                    nli = i + 1;
                }
            }
            return ng;
        }

        private void checkNodeLayers(Technology.NodeLayer[][] nls) {
            Xml.PrimitiveNodeGroup ng = xmlSch.findNodeGroup(getName());
            assert !ng.shrinkArcs && !isArcsShrink() && !isArcsWipe();
            assert ng.square == isSquare();
            assert ng.canBeZeroSize == isCanBeZeroSize();
            assert ng.wipes == isWipeOn1or2();
            assert !ng.lockable && !isLockedPrim();
            assert !ng.edgeSelect && !isEdgeSelect();
            assert !ng.skipSizeInPalette && !isSkipSizeInPalette();
            assert !ng.notUsed && !isNotUsed();
            assert ng.specialType == 0 && getSpecialType() == 0;
            assert ng.specialValues == null && getSpecialValues() == null;
            assert ng.spiceTemplate == null && getSpiceTemplate() == null;
            assert ng.defaultWidth.k == 0 && ng.defaultWidth.value == 0;
            assert ng.defaultHeight.k == 0 && ng.defaultHeight.value == 0;
            assert getFactoryDefaultSize() == EPoint.ORIGIN;
            assert ng.protection == null;
            assert ng.nodeSizeRule == null && getMinSizeRule() == null;

            EPoint baseCorrector = ng.diskOffset.get(Integer.valueOf(2));
            if (baseCorrector == null) {
                baseCorrector = EPoint.ORIGIN;
            }
            EPoint fullCorrector = ng.diskOffset.get(Integer.valueOf(1));
            if (fullCorrector == null) {
                fullCorrector = baseCorrector;
            }

            ERectangle base = getBaseRectangle();
            assert ng.baseLX.k == -1 && ng.baseLY.k == -1 && ng.baseHX.k == 1 && ng.baseHY.k == 1;
            assert ng.baseLX.value == base.getMinX() && ng.baseLY.value == base.getMinY() && ng.baseHX.value == base.getMaxX() && ng.baseHY.value == base.getMaxY();
            assert ng.baseHX.value - ng.baseLX.value == 2 * baseCorrector.getX();
            assert ng.baseHY.value - ng.baseLY.value == 2 * baseCorrector.getY();

            ERectangle full = getFullRectangle();
            assert full.getMinX() == -fullCorrector.getX() && full.getMaxX() == fullCorrector.getX();
            assert full.getMinY() == -fullCorrector.getY() && full.getMaxY() == fullCorrector.getY();

            assert altNodeLayers == nls;
            assert ng.nodes.size() == nls.length + 1;
            assert ng.nodes.get(0).function == getFunction();
            checkNodeLayers(ng.nodeLayers, 0, getNodeLayers());
            assert getElectricalLayers() == null;
            for (int i = 1; i < ng.nodes.size(); i++) {
                int techBits = i - 1;
                assert ng.nodes.get(i).function == getPrimitiveFunction(techBits);
                assert ng.nodes.get(i).function == altFunctions[techBits];
                checkNodeLayers(ng.nodeLayers, i, nls[techBits]);
            }
        }

        private void checkNodeLayers(List<Xml.NodeLayer> nls1, int ind, Technology.NodeLayer[] nls2) {
            int i = 0;
            for (Xml.NodeLayer nl1 : nls1) {
                if (!nl1.inNodes.get(ind)) {
                    continue;
                }
                Technology.NodeLayer nl2 = nls2[i++];
                assert nl1.layer.equals(nl2.getLayer().getName());
                assert nl1.style == nl2.getStyle();
                assert nl1.portNum == nl2.getPortNum();
                assert nl1.inLayers;
                assert nl1.inElectricalLayers;
                assert nl1.representation == nl2.getRepresentation();
                switch (nl1.representation) {
                    case Technology.NodeLayer.POINTS:
                        assert nl1.techPoints.size() == nl2.getPoints().length;
                        for (int j = 0; j < nl1.techPoints.size(); j++) {
                            assert nl1.techPoints.get(j).getX().equals(nl2.getPoints()[j].getX());
                            assert nl1.techPoints.get(j).getY().equals(nl2.getPoints()[j].getY());
                        }
                        break;
                    case Technology.NodeLayer.BOX:
                        assert nl1.techPoints.isEmpty();
                        assert nl2.getPoints().length == 2;
                        assert nl1.lx.k == nl2.getPoints()[0].getX().getMultiplier() * 2;
                        assert nl1.ly.k == nl2.getPoints()[0].getY().getMultiplier() * 2;
                        assert nl1.hx.k == nl2.getPoints()[1].getX().getMultiplier() * 2;
                        assert nl1.hy.k == nl2.getPoints()[1].getY().getMultiplier() * 2;
                        assert nl1.lx.value == nl2.getPoints()[0].getX().getAdder().getLambda();
                        assert nl1.ly.value == nl2.getPoints()[0].getY().getAdder().getLambda();
                        assert nl1.hx.value == nl2.getPoints()[1].getX().getAdder().getLambda();
                        assert nl1.hy.value == nl2.getPoints()[1].getY().getAdder().getLambda();
                        break;
                    default:
                        throw new AssertionError();
                }
                if (nl1.getMessage() != null) {
                    assert nl1.getMessage().equals(nl2.getMessage());
                } else {
                    assert nl2.getMessage() == null;
                }
                if (nl1.getRelSize() != 0) {
                    TextDescriptor td = nl2.getDescriptor();
                    assert !td.getSize().isAbsolute();
                    assert nl1.getRelSize() == nl2.getDescriptor().getSize().getSize();
                } else {
                    assert nl2.getDescriptor() == TextDescriptor.EMPTY;
                }
            }
            assert i == nls2.length;
        }
    }

    private class OffPageNode extends ExtraBlobsNode {

        private final Technology.NodeLayer[] offPageOutputLayers =
                new Technology.NodeLayer[]{
            new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-1)),
                new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(0)),
                new Technology.TechPoint(LEFT4BYP5, EdgeV.c(0)),
                new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(0)),
                new Technology.TechPoint(EdgeH.l(-2), EdgeV.t(1)),
                new Technology.TechPoint(RIGHT4BYP5, EdgeV.t(1)),
                new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                new Technology.TechPoint(RIGHT4BYP5, EdgeV.b(-1)),}),};
        private final Technology.NodeLayer[] offPageInputLayers =
                new Technology.NodeLayer[]{
            new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-1)),
                new Technology.TechPoint(EdgeH.l(-2), EdgeV.t(1)),
                new Technology.TechPoint(RIGHT4BYP5, EdgeV.t(1)),
                new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                new Technology.TechPoint(RIGHT4BYP5, EdgeV.c(0)),
                new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                new Technology.TechPoint(RIGHT4BYP5, EdgeV.b(-1)),}),};
        private final Technology.NodeLayer[] offPageBidirectionalLayers =
                new Technology.NodeLayer[]{
            new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                new Technology.TechPoint(LEFT4BYP5, EdgeV.b(-1)),
                new Technology.TechPoint(EdgeH.l(-2), EdgeV.c(0)),
                new Technology.TechPoint(LEFT4BYP5, EdgeV.t(1)),
                new Technology.TechPoint(RIGHT4BYP5, EdgeV.t(1)),
                new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                new Technology.TechPoint(RIGHT4BYP5, EdgeV.b(-1)),}),};

        private OffPageNode() {
            super("Off-Page", 4, 2, new Technology.NodeLayer[]{
                        new Technology.NodeLayer(node_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.l(-2), EdgeV.b(-1)),
                            new Technology.TechPoint(EdgeH.l(-2), EdgeV.t(1)),
                            new Technology.TechPoint(RIGHT4BYP5, EdgeV.t(1)),
                            new Technology.TechPoint(EdgeH.r(2), EdgeV.c(0)),
                            new Technology.TechPoint(RIGHT4BYP5, EdgeV.b(-1)),}),});
            addPrimitivePorts(
                    PrimitivePort.newInstance(this, new ArcProto[]{wire_arc, bus_arc}, "a", 180, 45, 0, PortCharacteristic.UNKNOWN,
                    EdgeH.l(-2), EdgeV.c(0), EdgeH.l(-2), EdgeV.c(0)),
                    PrimitivePort.newInstance(this, new ArcProto[]{wire_arc, bus_arc}, "y", 0, 45, 0, PortCharacteristic.UNKNOWN,
                    EdgeH.r(2), EdgeV.c(0), EdgeH.r(2), EdgeV.c(0)));
            setFunction(PrimitiveNode.Function.CONNECT);
        }

        /**
         * Puts into shape builder s the polygons that describe node "n", given a set of
         * NodeLayer objects to use.
         * This method is overridden by specific Technologies.
         * @param b shape builder where to put polygons
         * @param n the ImmutableNodeInst that is being described.
         */
        @Override
        public void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            assert n.protoId == getId();
            Technology.NodeLayer[] primLayers;
            boolean input = false;
            boolean output = false;
            boolean bidirectional = false;
            for (ImmutableExport e : i2i(b.getExportsOnNode(n))) {
                if (e.characteristic == PortCharacteristic.IN) {
                    input = true;
                } else if (e.characteristic == PortCharacteristic.OUT) {
                    output = true;
                } else if (e.characteristic == PortCharacteristic.BIDIR) {
                    bidirectional = true;
                }
            }
            if (input && !output && !bidirectional) {
                primLayers = offPageInputLayers;
            } else if (!input && output && !bidirectional) {
                primLayers = offPageOutputLayers;
            } else if (!input && !output && bidirectional) {
                primLayers = offPageBidirectionalLayers;
            } else {
                primLayers = getNodeLayers();
            }
            genShapeOfNode(b, n, primLayers);
        }
    }
}
