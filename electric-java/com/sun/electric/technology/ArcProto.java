/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ArcProto.java
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
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.id.ArcProtoId;
import com.sun.electric.database.id.PrimitiveNodeId;
import com.sun.electric.database.id.PrimitivePortId;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.erc.ERCAntenna;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.ECoord;

import com.sun.electric.util.math.FixpCoord;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The ArcProto class defines a type of ArcInst.
 * <P>
 * Every arc in the database appears as one <I>prototypical</I> object and many <I>instantiative</I> objects.
 * Thus, for a ArcProto such as the CMOS Metal-1 there is one object (called a ArcProto)
 * that describes the wire prototype and there are many objects (called ArcInsts),
 * one for every instance of a Metal-1 wire that appears in a circuit.
 * ArcProtos are statically created and placed in the Technology objects.
 * <P>
 * The basic ArcProto has a name, default width, function, Layers that describes it graphically and more.
 */
public class ArcProto implements Comparable<ArcProto>, Serializable {

    /**
     * Function is a typesafe enum class that describes the function of an ArcProto.
     * Functions are technology-independent and include different types of metal,
     * polysilicon, and other basic wire types.
     */
    public static enum Function {

        /** Describes an arc with unknown type. */
        UNKNOWN("unknown", 0, 0),
        
        /** Describes an arc on Metal layer 1. */
        METAL1("metal-1", 1, 0),
        /** Describes an arc on Metal layer 2. */
        METAL2("metal-2", 2, 0),
        /** Describes an arc on Metal layer 3. */
        METAL3("metal-3", 3, 0),
        /** Describes an arc on Metal layer 4. */
        METAL4("metal-4", 4, 0),
        /** Describes an arc on Metal layer 5. */
        METAL5("metal-5", 5, 0),
        /** Describes an arc on Metal layer 6. */
        METAL6("metal-6", 6, 0),
        /** Describes an arc on Metal layer 7. */
        METAL7("metal-7", 7, 0),
        /** Describes an arc on Metal layer 8. */
        METAL8("metal-8", 8, 0),
        /** Describes an arc on Metal layer 9. */
        METAL9("metal-9", 9, 0),
        /** Describes an arc on Metal layer 10. */
        METAL10("metal-10", 10, 0),
        /** Describes an arc on Metal layer 11. */
        METAL11("metal-11", 11, 0),
        /** Describes an arc on Metal layer 12. */
        METAL12("metal-12", 12, 0),
        /** Describes an arc on Metal layer 13. */
        METAL13("metal-13", 13, 0),
        /** Describes an arc on Metal layer 14. */
        METAL14("metal-14", 14, 0),
        
        /** Describes an arc on Polysilicon layer 1. */
        POLY1("poly-1", 0, 1),
        /** Describes an arc on Polysilicon layer 2. */
        POLY2("poly-2", 0, 2),
        /** Describes an arc on Polysilicon layer 3. */
        POLY3("poly-3", 0, 3),
        /** Describes a dummy polysilicon layer 1 */
        DMYPOLY1("dmy-poly-1", 0, 0),
        /** Describes an arc on the Diffusion layer. */
        DIFF("diffusion", 0, 0),
        /** Describes an arc on the P-Diffusion layer. */
        DIFFP("p-diffusion", 0, 0),
        /** Describes an arc on the N-Diffusion layer. */
        DIFFN("n-diffusion", 0, 0),
        /** Describes an arc on the Substrate-Diffusion layer. */
        DIFFS("substrate-diffusion", 0, 0),
        /** Describes an arc on the Well-Diffusion layer. */
        DIFFW("well-diffusion", 0, 0),
        /** Describes an arc on the Well layer (bias connections). */
        WELL("well", 0, 0),
        /** Describes a bus arc. */
        BUS("bus", 0, 0),
        /** Describes an arc that is unrouted (to be replaced by routers). */
        UNROUTED("unrouted", 0, 0),
        /** Describes an arc that is non-electrical (does not make a circuit connection). */
        NONELEC("nonelectrical", 0, 0);
        private final String printName;
        private final int level;
        private final boolean isMetal;
        private final boolean isPoly;
        private final boolean isDiffusion;
        private static final Function[] metalLayers = initMetalLayers(Function.class.getEnumConstants());
        private static final Function[] polyLayers = initPolyLayers(Function.class.getEnumConstants());

        private Function(String printName, int metalLevel, int polyLevel) {
            this.printName = printName;
            isMetal = metalLevel != 0;
            isPoly = polyLevel != 0;
            isDiffusion = name().startsWith("DIFF");
            level = isMetal ? metalLevel : isPoly ? polyLevel : 0;
        }

        /**
         * Returns a printable version of this ArcProto.
         * @return a printable version of this ArcProto.
         */
        public String toString() {
            return printName;
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
         * Method to return a List of all ArcProto functions.
         * @return a List of all ArcProto functions.
         */
        public static List<Function> getFunctions() {
            return Arrays.asList(Function.class.getEnumConstants());
        }

        /**
         * Method to get the level of this ArcProto.Function.
         * The level applies to metal and polysilicon functions, and gives the layer number
         * (i.e. Metal-2 is level 2).
         * @return the level of this ArcProto.Function.
         */
        public int getLevel() {
            return level;
        }

        /**
         * Method to find the Function that corresponds to Metal on a given layer.
         * @param level the layer (starting at 1 for Metal-1).
         * @return the Function that represents that Metal layer.
         */
        public static Function getMetal(int level) {
            return level < metalLayers.length ? metalLayers[level] : null;
        }

        /**
         * Method to find the Function that corresponds to Polysilicon on a given layer.
         * @param level the layer (starting at 1 for Polysilicon-1).
         * @return the Function that represents that Polysilicon layer.
         */
        public static Function getPoly(int level) {
            return level < polyLayers.length ? polyLayers[level] : null;
        }

        /**
         * Method to find the Function that corresponds to a contact on a given arc.
         * @param level the arc (starting at 1 for Contact-1).
         * @return the Function that represents that Contact arc.
         */
        public static Function getContact(int level) {
            return metalLayers[level];
        }

        /**
         * Method to tell whether this ArcProto.Function is metal.
         * @return true if this ArcProto.Function is metal.
         */
        public boolean isMetal() {
            return isMetal;
        }

        /**
         * Method to tell whether this ArcProto.Function is polysilicon.
         * @return true if this ArcProto.Function is polysilicon.
         */
        public boolean isPoly() {
            return isPoly;
        }

        /**
         * Method to tell whether this ArcProto.Function is diffusion.
         * @return true if this ArcProto.Function is diffusion.
         */
        public boolean isDiffusion() {
            return isDiffusion;
        }
        
        /**
         * Method to find a Function from its name.
         * @param name the name to find.
         * @return a Function (null if not found).
         */
        public static com.sun.electric.technology.ArcProto.Function findByPrintName(String name) {
            List<Function> allFuncs = getFunctions();
            for (Function fun : allFuncs) {
                if (fun.printName.equalsIgnoreCase(name)) {
                    return fun;
                }
            }
            return null;
        }

        private static Function[] initMetalLayers(Function[] allFunctions) {
            int maxLevel = -1;
            for (Function fun : getFunctions()) {
                if (!fun.isMetal()) {
                    continue;
                }
                maxLevel = Math.max(maxLevel, fun.level);
            }
            Function[] layers = new Function[maxLevel + 1];
            for (Function fun : getFunctions()) {
                if (!fun.isMetal()) {
                    continue;
                }
                assert layers[fun.level] == null;
                layers[fun.level] = fun;
            }
            return layers;
        }

        private static Function[] initPolyLayers(Function[] allFunctions) {
            int maxLevel = -1;
            for (Function fun : getFunctions()) {
                if (!fun.isPoly()) {
                    continue;
                }
                maxLevel = Math.max(maxLevel, fun.level);
            }
            Function[] layers = new Function[maxLevel + 1];
            for (Function fun : getFunctions()) {
                if (!fun.isPoly()) {
                    continue;
                }
                assert layers[fun.level] == null;
                layers[fun.level] = fun;
            }
            return layers;
        }
    }
    // ----------------------- private data -------------------------------
    /** The name of this ArcProto. */
    private final ArcProtoId protoId;
    /** The technology in which this ArcProto resides. */
    private final Technology tech;
    /** The ELIB width offset */
    private final double lambdaElibWidthOffset;
    /** The base extend of this ArcProto in grid units. */
    private ECoord baseExtend;
    /** The minimum extend among ArcLayers. */
    private ECoord minLayerExtend;
    /** The minimum extend among ArcLayers. */
    private ECoord maxLayerExtend;
    /** Flags bits for this ArcProto. */
    private int userBits;
    /** The function of this ArcProto. */
    final Function function;
    /** Layers in this arc */
    final Technology.ArcLayer[] layers;
    /** Pin for this arc */
    PrimitiveNode arcPin;
    /** Index of this ArcProto. */
    final int primArcIndex;
    /** factory default instance */
    ImmutableArcInst factoryDefaultInst;
    /** factory arc angle increment. */
    private int factoryAngleIncrement = 90;
    /** Factory value for arc antenna ratio. */
    private double factoryAntennaRatio = Double.NaN;
    // the meaning of the "userBits" field:
//	/** these arcs are fixed-length */							private static final int WANTFIX  =            01;
//	/** these arcs are fixed-angle */							private static final int WANTFIXANG  =         02;
//	/** set if arcs should not slide in ports */				private static final int WANTCANTSLIDE  =      04;
//	/** set if ends do not extend by half width */				private static final int WANTNOEXTEND  =      010;
//	/** set if arcs should be negated */						private static final int WANTNEGATED  =       020;
//	/** set if arcs should be directional */					private static final int WANTDIRECTIONAL  =   040;
    /** set if arcs can wipe wipable nodes */
    private static final int CANWIPE = 0100;
    /** set if arcs can curve */
    private static final int CANCURVE = 0200;
//	/** arc function (from efunction.h) */						private static final int AFUNCTION  =      017400;
//	/** right shift for AFUNCTION */							private static final int AFUNCTIONSH  =         8;
//	/** angle increment for this type of arc */					private static final int AANGLEINC  =   017760000;
//	/** right shift for AANGLEINC */							private static final int AANGLEINCSH  =        13;
    /** set if arc is not selectable in palette */
    private static final int ARCSPECIAL = 010000000;
    /** set if arc is selectable by edge, not area */
    private static final int AEDGESELECT = 020000000;
//	/** set if arc is invisible and unselectable */				private static final int AINVISIBLE   = 040000000;
    /** set if arc is not used */
    private static final int ANOTUSED = 020000000000;
    /** set if node will be considered in palette */
    private static final int SKIPSIZEINPALETTE = 0400;

    // ----------------- protected and private methods -------------------------
    /**
     * The constructor is never called.  Use "Technology.newArcProto" instead.
     */
    protected ArcProto(Technology tech, String protoName, double lambdaElibWidthOffset, Function function, Technology.ArcLayer[] layers, int primArcIndex) {
        if (!Technology.jelibSafeName(protoName)) {
            System.out.println("ArcProto name " + protoName + " is not safe to write into JELIB");
        }
        protoId = tech.getId().newArcProtoId(protoName);
        this.tech = tech;
        this.userBits = 0;
        this.function = function;
        this.layers = layers.clone();
        this.primArcIndex = primArcIndex;
        this.lambdaElibWidthOffset = lambdaElibWidthOffset;
        this.baseExtend = layers[0].getExtend();
        assert -Integer.MAX_VALUE / 8 < baseExtend.getGrid() && baseExtend.getGrid() < Integer.MAX_VALUE / 8;
        computeLayerGridExtendRange();
        PrimitivePortId ppId = protoId.techId.idManager.newTechId("generic").newPrimitiveNodeId("Universal-Pin").newPortId("");
        factoryDefaultInst = ImmutableArcInst.newInstance(0, protoId, ImmutableArcInst.BASENAME, null,
                0, ppId, EPoint.ORIGIN, 0, ppId, EPoint.ORIGIN, 0, 0, ImmutableArcInst.FACTORY_DEFAULT_FLAGS);
    }

    private void computeLayerGridExtendRange() {
        ECoord min = ECoord.MAX_ECOORD;
        ECoord max = ECoord.MIN_ECOORD;
        for (int i = 0; i < layers.length; i++) {
            Technology.ArcLayer primLayer = layers[i];
            assert indexOf(primLayer.getLayer()) == i; // layers are unique
            min = min.min(getLayerExtend(i));
            max = max.max(getLayerExtend(i));
        }
        assert -Integer.MAX_VALUE / 8 < min.getGrid();
//        assert 0 <= min;
        assert max.getGrid() < Integer.MAX_VALUE / 8 && min.compareTo(max) <= 0;
        minLayerExtend = min;
        maxLayerExtend = max;
    }

    protected Object writeReplace() {
        return new ArcProtoKey(this);
    }

    private static class ArcProtoKey extends EObjectInputStream.Key<ArcProto> {

        public ArcProtoKey() {
        }

        private ArcProtoKey(ArcProto ap) {
            super(ap);
        }

        @Override
        public void writeExternal(EObjectOutputStream out, ArcProto ap) throws IOException {
            out.writeObject(ap.getTechnology());
            out.writeInt(ap.getId().chronIndex);
        }

        @Override
        public ArcProto readExternal(EObjectInputStream in) throws IOException, ClassNotFoundException {
            Technology tech = (Technology) in.readObject();
            int chronIndex = in.readInt();
            ArcProto ap = tech.getArcProtoByChronIndex(chronIndex);
            if (ap == null) {
                throw new InvalidObjectException("arc proto not found");
            }
            return ap;
        }
    }

    public static class Curvable extends ArcProto {

        protected Curvable(Technology tech, String protoName, double lambdaElibWidthOffset, Function function, Technology.ArcLayer[] layers, int primArcIndex) {
            super(tech, protoName, lambdaElibWidthOffset, function, layers, primArcIndex);
            setCurvable();
        }

        /**
         * Tells if arc can be drawn by simplified algorithm
         * Overidden ins subclasses
         * @param a arc to test
         * @param explain if true then print explanation why arc is not easy
         * @return true if arc can be drawn by simplified algorithm
         */
        @Override
        public boolean isEasyShape(ImmutableArcInst a, boolean explain) {
            if (a.getVar(ImmutableArcInst.ARC_RADIUS) != null) {
                if (explain) {
                    System.out.println("CURVABLE");
                }
                return false;
            }
            return super.isEasyShape(a, explain);
        }
        
        /**
         * Method to fill in an AbstractShapeBuilder a polygon that describes this ImmutableArcInst in grid units.
         * The polygon is described by its width, and style.
         * @param a the arc information.
         * @param gridWidth the gridWidth of the Poly.
         * @param style the style of the Poly.
         * @param layer layer of the Poly
         * @param graphicsOverride graphics override of the Poly
         */
        @Override
        public void makeGridPoly(AbstractShapeBuilder b, ImmutableArcInst a, long gridWidth, Poly.Type style, Layer layer, EGraphics graphicsOverride) {
            // get the radius information on the arc
            Double radiusDouble = a.getRadius();
            if (radiusDouble != null && curvedArcGridOutline(b, a, gridWidth, DBMath.lambdaToGrid(radiusDouble))) {
                b.pushPoly(style, layer, graphicsOverride, null);
                return;
            }
            super.makeGridPoly(b, a, gridWidth, style, layer, graphicsOverride);
        }
        /**
         * when arcs are curved, the number of line segments will be
         * between this value, and half of this value.
         */
        private static final int MAXARCPIECES = 16;

        /**
         * Method to fill polygon "poly" with the outline in grid units of the curved arc in
         * this ImmutableArcInst whose width in grid units is "gridWidth".
         * If there is no curvature information in the arc, the routine returns false,
         * otherwise it returns the curved polygon.
         * @param a the arc information.
         * @param gridWidth width in grid units.
         * @param gridRadius radius in grid units.
         * @return true if point were filled to the buuilder
         */
        private boolean curvedArcGridOutline(AbstractShapeBuilder b, ImmutableArcInst a, long gridWidth, long gridRadius) {
            // get information about the curved arc
            long pureGridRadius = Math.abs(gridRadius);
            double gridLength = a.getGridLength();

            // see if the lambdaRadius can work with these arc ends
            if (pureGridRadius * 2 < gridLength) {
                return false;
            }

            // determine the center of the circle
            Point2D[] centers = DBMath.findCenters(pureGridRadius, a.headLocation.gridMutable(), a.tailLocation.gridMutable());
            if (centers == null) {
                return false;
            }

            Point2D centerPt = gridRadius >= 0 ? centers[1] : centers[0];
            double centerX = centerPt.getX();
            double centerY = centerPt.getY();

            // determine the base and range of angles
            int angleBase = DBMath.figureAngle(a.headLocation.getGridX() - centerX, a.headLocation.getGridY() - centerY);
            int angleRange = DBMath.figureAngle(a.tailLocation.getGridX() - centerX, a.tailLocation.getGridY() - centerY);
            angleRange -= angleBase;
            if (angleRange < 0) {
                angleRange += 3600;
            }

            // force the curvature to be the smaller part of a circle (used to determine this by the reverse-ends bit)
            if (angleRange > 1800) {
                angleBase += angleRange;
                if (angleBase < 0) {
                    angleBase += 3600;
                }
                angleRange = 3600 - angleRange;
            }

            // determine the number of intervals to use for the arc
            int pieces = angleRange;
            while (pieces > MAXARCPIECES) {
                pieces /= 2;
            }
            if (pieces == 0) {
                return false;
            }

            // get the inner and outer radii of the arc
            double outerRadius = pureGridRadius + gridWidth / 2;
            double innerRadius = outerRadius - gridWidth;

            // fill the polygon
            for (int i = 0; i <= pieces; i++) {
                int angle = (angleBase + i * angleRange / pieces) % 3600;
                b.pushPoint((DBMath.cos(angle) * innerRadius + centerX) * FixpCoord.FIXP_SCALE,
                        (DBMath.sin(angle) * innerRadius + centerY) * FixpCoord.FIXP_SCALE);
            }
            for (int i = pieces; i >= 0; i--) {
                int angle = (angleBase + i * angleRange / pieces) % 3600;
                b.pushPoint((DBMath.cos(angle) * outerRadius + centerX) * FixpCoord.FIXP_SCALE,
                        (DBMath.sin(angle) * outerRadius + centerY) * FixpCoord.FIXP_SCALE);
            }
            return true;
        }

    }

    // ------------------------ public methods -------------------------------
    /**
     * Method to return the Id of this ArcProto.
     * @return the Id of this ArcProto.
     */
    public ArcProtoId getId() {
        return protoId;
    }

    /**
     * Method to return the name of this ArcProto.
     * @return the name of this ArcProto.
     */
    public String getName() {
        return protoId.name;
    }

    /**
     * Method to return the full name of this ArcProto.
     * Full name has format "techName:primName"
     * @return the full name of this ArcProto.
     */
    public String getFullName() {
        return protoId.fullName;
    }

    /**
     * Method to return the Technology of this ArcProto.
     * @return the Technology of this ArcProto.
     */
    public Technology getTechnology() {
        return tech;
    }

    /**
     * Method to return the default base width of this ArcProto in lambda units.
     * This is the reported/selected width, which means that it does not include the width offset.
     * For example, diffusion arcs are always accompanied by a surrounding well and select.
     * This call returns only the width of the diffusion.
     * @return the default base width of this ArcProto in lambda units.
     * @deprecated Use method with explicit EditingPreferences parameter.
     */
    public double getDefaultLambdaBaseWidth() {
        return getDefaultLambdaBaseWidth(EditingPreferences.getInstance());
    }

    /**
     * Method to return the default base width of this ArcProto in lambda units.
     * This is the reported/selected width, which means that it does not include the width offset.
     * For example, diffusion arcs are always accompanied by a surrounding well and select.
     * This call returns only the width of the diffusion.
     * @param ep EditingPreferences
     * @return the default base width of this ArcProto in lambda units.
     */
    public double getDefaultLambdaBaseWidth(EditingPreferences ep) {
        return DBMath.gridToLambda(getDefaultGridBaseWidth(ep));
    }

    /**
     * Method to return the factory default base width of this ArcProto in lambda units.
     * This is the reported/selected width, which means that it does not include the width offset.
     * For example, diffusion arcs are always accompanied by a surrounding well and select.
     * This call returns only the width of the diffusion.
     * @return the factory default base width of this ArcProto in lambda units.
     */
    public double getFactoryDefaultLambdaBaseWidth() {
        return DBMath.gridToLambda(getFactoryDefaultGridBaseWidth());
    }

    /**
     * Method to return the default base width of this ArcProto in grid units.
     * This is the reported/selected width, which means that it does not include the width offset.
     * For example, diffusion arcs are always accompanied by a surrounding well and select.
     * This call returns only the width of the diffusion.
     * @param ep EditingPreferences
     * @return the default base width of this ArcProto in grid units.
     */
    public long getDefaultGridBaseWidth(EditingPreferences ep) {
        return 2 * (getDefaultInst(ep).getGridExtendOverMin() + baseExtend.getGrid());
    }

    /**
     * Method to return the factory default base width of this ArcProto in grid units.
     * This is the reported/selected width, which means that it does not include the width offset.
     * For example, diffusion arcs are always accompanied by a surrounding well and select.
     * This call returns only the width of the diffusion.
     * @return the factory default base width of this ArcProto in grid units.
     */
    public long getFactoryDefaultGridBaseWidth() {
        return 2 * (factoryDefaultInst.getGridExtendOverMin() + baseExtend.getGrid());
    }

    /**
     * Method to return the default immutable instance of this PrimitiveNode
     * in specified EditingPreferences.
     * @param ep specified EditingPreferences
     * @return the default immutable instance of this PrimitiveNode
     */
    public ImmutableArcInst getDefaultInst(EditingPreferences ep) {
        ImmutableArcInst defaultInst = ep.getDefaultArc(protoId);
        return defaultInst != null ? defaultInst : factoryDefaultInst;
    }

    /**
     * Method to return the factory default immutable instance of this PrimitiveNode
     * @return the factory default immutable instance of this PrimitiveNode
     */
    public ImmutableArcInst getFactoryDefaultInst() {
        return factoryDefaultInst;
    }

    /**
     * Method to return the base width extend of this ArcProto as ECoord object.
     * This is the reported/selected width.
     * For example, diffusion arcs are always accompanied by a surrounding well and select.
     * This call returns only the half width of the diffusion of minimal-width arc.
     * @return the default base width extend of this ArcProto as ECoord object.
     */
    public ECoord getBaseExtend() {
        return baseExtend;
    }

    /**
     * Method to return the width offset of this ArcProto in lambda units.
     * The width offset excludes the surrounding implang material.
     * For example, diffusion arcs are always accompanied by a surrounding well and select.
     * The offset amount is the difference between the diffusion width and the overall width.
     * @return the width offset of this ArcProto in lambda units.
     */
    public double getLambdaElibWidthOffset() {
        return lambdaElibWidthOffset;
    }

    /**
     * Method to return the minimal layer extend of this ArcProto as ECoord object.
     * @return the minimal layer extend of this ArcProto as ECoord object.
     */
    public ECoord getMinLayerExtend() {
        return minLayerExtend;
    }

    /**
     * Method to return the maximal layer extend of this ArcProto as ECoord object.
     * @return the maximal layer extend of this ArcProto as ECoord object.
     */
    public ECoord getMaxLayerExtend() {
        return maxLayerExtend;
    }

    /**
     * Method to set the factory antenna ratio of this ArcProto.
     * Antenna ratios are used in antenna checks that make sure the ratio of the area of a layer is correct.
     * @param ratio the antenna ratio of this ArcProto.
     */
    public void setFactoryAntennaRatio(double ratio) {
        assert Double.isNaN(factoryAntennaRatio);
        factoryAntennaRatio = ratio;
    }

    /**
     * Method to tell the default antenna ratio of this ArcProto.
     * Antenna ratios are used in antenna checks that make sure the ratio of the area of a layer is correct.
     * @return the default antenna ratio of this ArcProto.
     */
    public double getFactoryAntennaRatio() {
        return factoryAntennaRatio;
    }

    /**
     * Method to set the "factory default" rigid state of this ArcProto.
     * Rigid arcs cannot change length or the angle of their connection to a NodeInst.
     * @param rigid true if this ArcProto should be rigid by factory-default.
     */
    public void setFactoryRigid(boolean rigid) {
        factoryDefaultInst = factoryDefaultInst.withFlag(ImmutableArcInst.RIGID, rigid);
    }

    /**
     * Method to set the "factory default" fixed-angle state of this ArcProto.
     * Fixed-angle arcs cannot change their angle, so if one end moves,
     * the other may also adjust to keep the arc angle constant.
     * @param fixed true if this ArcProto should be fixed-angle by factory-default.
     */
    public void setFactoryFixedAngle(boolean fixed) {
        factoryDefaultInst = factoryDefaultInst.withFlag(ImmutableArcInst.FIXED_ANGLE, fixed);
    }

    /**
     * Method to set the "factory default" slidability state of this ArcProto.
     * Arcs that slide will not move their connected NodeInsts if the arc's end is still within the port area.
     * Arcs that cannot slide will force their NodeInsts to move by the same amount as the arc.
     * Rigid arcs cannot slide but nonrigid arcs use this state to make a decision.
     * @param slidable true if this ArcProto should be slidability by factory-default.
     */
    public void setFactorySlidable(boolean slidable) {
        factoryDefaultInst = factoryDefaultInst.withFlag(ImmutableArcInst.SLIDABLE, slidable);
    }

    /**
     * Method to set the "factory default" end-extension state of this ArcProto.
     * End-extension causes an arc to extend past its endpoint by half of its width.
     * Most layout arcs want this so that they make clean connections to orthogonal arcs.
     * @param extended true if this ArcProto should be end-extended by factory-default.
     */
    public void setFactoryExtended(boolean extended) {
        factoryDefaultInst = factoryDefaultInst.withFlag(ImmutableArcInst.TAIL_EXTENDED, extended).
                withFlag(ImmutableArcInst.HEAD_EXTENDED, extended);
    }

    /**
     * Method to set the "factory default" directional state of this ArcProto.
     * Directionality causes arrows to be drawn at the head, tail, or center of the arc.
     * @param defaultDir has bit 0 set to put arrow on head, bit 1 set to put arrow on tail,
     * bit 2 set to put arrow on body.
     */
    public void setFactoryDirectional(int defaultDir) {
        if ((defaultDir & 1) != 0) {
            factoryDefaultInst = factoryDefaultInst.withFlag(ImmutableArcInst.HEAD_ARROWED, true);
        }
        if ((defaultDir & 2) != 0) {
            factoryDefaultInst = factoryDefaultInst.withFlag(ImmutableArcInst.TAIL_ARROWED, true);
        }
        if ((defaultDir & 4) != 0) {
            factoryDefaultInst = factoryDefaultInst.withFlag(ImmutableArcInst.BODY_ARROWED, true);
        }
    }

    /**
     * Method to set this ArcProto so that it is not used.
     * Unused arcs do not appear in the component menus and cannot be created by the user.
     * The state is useful for hiding arcs that the user should not use.
     * @param set
     */
    public void setNotUsed(boolean set) {
        /* checkChanging();*/
        if (set) {
            userBits |= ANOTUSED;
        } else {
            userBits &= ~ANOTUSED;
        }
        if (arcPin != null) {
            arcPin.setNotUsed(set);
        }
    }

    /**
     * Method to tell if this ArcProto is used.
     * Unused arcs do not appear in the component menus and cannot be created by the user.
     * The state is useful for hiding arcs that the user should not use.
     * @return true if this ArcProto is used.
     */
    public boolean isNotUsed() {
        return (userBits & ANOTUSED) != 0;
    }

    /**
     * Method to allow instances of this ArcProto not to be considered in
     * tech palette for the calculation of the largest icon.
     * Valid for menu display
     */
    public void setSkipSizeInPalette() {
        userBits |= SKIPSIZEINPALETTE;
    }

    /**
     * Method to tell if instaces of this ArcProto are special (don't appear in menu).
     * Valid for menu display
     */
    public boolean isSkipSizeInPalette() {
        return (userBits & SKIPSIZEINPALETTE) != 0;
    }

    /**
     * Method to set this ArcProto so that instances of it can wipe nodes.
     * For display efficiency reasons, pins that have arcs connected to them should not bother being drawn.
     * Those arc prototypes that can erase their connecting pins have this state set,
     * and when instances of these arcs connect to the pins, those pins stop being drawn.
     * It is necessary for the pin node prototype to enable wiping (with setArcsWipe).
     * A NodeInst that becomes wiped out has "setWiped" called.
     */
    public void setWipable() {
        userBits |= CANWIPE;
    }

    /**
     * Method to set this ArcProto so that instances of it cannot wipe nodes.
     * For display efficiency reasons, pins that have arcs connected to them should not bother being drawn.
     * Those arc prototypes that can erase their connecting pins have this state set,
     * and when instances of these arcs connect to the pins, those pins stop being drawn.
     * It is necessary for the pin node prototype to enable wiping (with setArcsWipe).
     * A NodeInst that becomes wiped out has "setWiped" called.
     */
    public void clearWipable() {
        userBits &= ~CANWIPE;
    }

    /**
     * Method to tell if instances of this ArcProto can wipe nodes.
     * For display efficiency reasons, pins that have arcs connected to them should not bother being drawn.
     * Those arc prototypes that can erase their connecting pins have this state set,
     * and when instances of these arcs connect to the pins, those pins stop being drawn.
     * It is necessary for the pin node prototype to enable wiping (with setArcsWipe).
     * A NodeInst that becomes wiped out has "setWiped" called.
     * @return true if instances of this ArcProto can wipe nodes.
     */
    public boolean isWipable() {
        return (userBits & CANWIPE) != 0;
    }

    /**
     * Method to set this ArcProto so that instances of it can curve.
     * Since arc curvature is complex to draw, arcs with this capability
     * must be marked this way.
     * A curved arc has the variable "arc_radius" on it with a curvature factor.
     */
    void setCurvable() {
        userBits |= CANCURVE;
    }

    /**
     * Method to set this ArcProto so that instances of it cannot curve.
     * Since arc curvature is complex to draw, arcs with this capability
     * must be marked this way.
     * A curved arc has the variable "arc_radius" on it with a curvature factor.
     */
    private void clearCurvable() {
        userBits &= ~CANCURVE;
    }

    /**
     * Method to tell if instances of this ArcProto can curve.
     * Since arc curvature is complex to draw, arcs with this capability
     * must be marked this way.
     * A curved arc has the variable "arc_radius" on it with a curvature factor.
     * @return true if instances of this ArcProto can curve.
     */
    public boolean isCurvable() {
        return (userBits & CANCURVE) != 0;
    }

    /**
     * Method to set this ArcProto so that instances of it can be selected by their edge.
     * Artwork primitives that are not filled-in or are outlines want edge-selection, instead
     * of allowing a click anywhere in the bounding box to work.
     */
    public void setEdgeSelect() {
        userBits |= AEDGESELECT;
    }

    /**
     * Method to set this ArcProto so that instances of it cannot be selected by their edge.
     * Artwork primitives that are not filled-in or are outlines want edge-selection, instead
     * of allowing a click anywhere in the bounding box to work.
     */
    public void clearEdgeSelect() {
        userBits &= ~AEDGESELECT;
    }

    /**
     * Method to tell if instances of this ArcProto can be selected by their edge.
     * Artwork primitives that are not filled-in or are outlines want edge-selection, instead
     * of allowing a click anywhere in the bounding box to work.
     * @return true if instances of this ArcProto can be selected by their edge.
     */
    public boolean isEdgeSelect() {
        return (userBits & AEDGESELECT) != 0;
    }

    /**
     * Method to allow instances of this ArcProto to be special in menu.
     * Valid for menu display
     */
    public void setSpecialArc() {
        userBits |= ARCSPECIAL;
    }

    /**
     * Method to tell if instaces of this ArcProto are special (don't appear in menu).
     * Valid for menu display
     */
    public boolean isSpecialArc() {
        return (userBits & ARCSPECIAL) != 0;
    }

    /**
     * Method to return the function of this ArcProto.
     * The Function is a technology-independent description of the behavior of this ArcProto.
     * @return function the function of this ArcProto.
     */
    public ArcProto.Function getFunction() {
        return function;
    }

    /**
     * Method to set the factory-default angle of this ArcProto.
     * This is only called from ArcProto during construction.
     * @param angle the factory-default angle of this ArcProto.
     */
    public void setFactoryAngleIncrement(int angle) {
        factoryAngleIncrement = angle;
    }

    /**
     * Method to get the angle increment on this ArcProto.
     * The angle increment is the granularity on placement angle for instances
     * of this ArcProto.  It is in degrees.
     * For example, a value of 90 requests that instances run at 0, 90, 180, or 270 degrees.
     * A value of 0 allows arcs to be created at any angle.
     * @param ep editing preferences with default increment
     * @return the angle increment on this ArcProto.
     */
    public int getAngleIncrement(EditingPreferences ep) {
        Integer angleIncrement = ep.getDefaultAngleIncrement(protoId);
        return angleIncrement != null ? angleIncrement.intValue() : factoryAngleIncrement;
    }

    /**
     * Method to get the default angle increment on this ArcProto.
     * The angle increment is the granularity on placement angle for instances
     * of this ArcProto.  It is in degrees.
     * For example, a value of 90 requests that instances run at 0, 90, 180, or 270 degrees.
     * A value of 0 allows arcs to be created at any angle.
     * @return the default angle increment on this ArcProto.
     */
    public int getFactoryAngleIncrement() {
        return factoryAngleIncrement;
    }

    /**
     * Method to find the PrimitiveNode pin corresponding to this ArcProto type.
     * Users can override the pin to use, and this method returns the user setting.
     * For example, if this ArcProto is metal-1 then return the Metal-1-pin,
     * but the user could set it to Metal-1-Metal-2-Contact.
     * @param ep editing preferences with user overrides
     * @return the PrimitiveNode pin to use for arc bends.
     */
    public PrimitiveNode findOverridablePinProto(EditingPreferences ep) {
        // see if there is a default on this arc proto
        PrimitiveNodeId pinId = ep.getDefaultArcPinId(protoId);
        if (pinId != null) {
            PrimitiveNode np = tech.getPrimitiveNode(pinId);
            if (np != null) {
                return np;
            }
        }
        return findPinProto();
    }

    /**
     * Method to find the PrimitiveNode pin corresponding to this ArcProto type.
     * For example, if this ArcProto is metal-1 then return the Metal-1-pin.
     * @return the PrimitiveNode pin to use for arc bends.
     */
    public PrimitiveNode findPinProto() {
        if (arcPin != null) {
            return arcPin;
        }

        // search for an appropriate pin
        Iterator<PrimitiveNode> it = tech.getNodes();
        while (it.hasNext()) {
            PrimitiveNode pn = it.next();
            if (pn.isPin()) {
                if (pn.getNumPorts() != 1) {
                    System.out.println("Missing cases in ArcProto:findPinProto - " + pn.getName());
                }
                if (pn.getNumPorts() == 1)
                {
                        PrimitivePort pp = (PrimitivePort) pn.getPorts().next();
                        if (pp.connectsTo(this)) {
                            return pn;
                        }
                }
            }
        }
        return null;
    }

//    public PrimitiveNode makeWipablePin(String pinName, String portName) {
//        double defSize = DBMath.round(2*getLambdaBaseExtend() + getLambdaElibWidthOffset());
//        return makeWipablePin(pinName, portName, defSize);
//    }
//    public PrimitiveNode makeWipablePin(String pinName, String portName, double defSize, ArcProto... extraArcs) {
//        double elibSize0 = DBMath.round(defSize * 0.5);
//        double elibSize1 = DBMath.round(elibSize0 - 0.5 * getLambdaElibWidthOffset());
//        arcPin = PrimitiveNode.makeArcPin(this, pinName, portName, elibSize0, elibSize1, extraArcs);
//        arcPin.setNotUsed(isNotUsed());
//        return arcPin;
//    }

	private static Map<ArcProto,Integer> maskLayers = new HashMap<ArcProto,Integer>();

	/**
	 * Method to return the mask layer for this ArcProto.
	 * The mask layers (or "colors") are the different masks needed to fabricate a single layer
	 * (used for double or triple patterning).
	 * @return the mask layer for this ArcProto.
	 */
	public int getMaskLayer()
	{
		Integer maskLayer = maskLayers.get(this);
		if (maskLayer == null)
		{
			for(Technology.ArcLayer al : getArcLayers())
			{
				Layer lay = al.getLayer();
				if (lay.getFunction().isMetal())
					maskLayer = Integer.valueOf(lay.getFunction().getMaskColor());
			}
			if (maskLayer == null) maskLayer = Integer.valueOf(0);
			maskLayers.put(this, maskLayer);
		}
		return maskLayer.intValue();
	}

    /**
     * Method to find the ArcProto with the given name.
     * This can be prefixed by a Technology name.
     * @param line the name of the ArcProto.
     * @return the specified ArcProto, or null if none can be found.
     */
    public static ArcProto findArcProto(String line) {
        Technology tech = Technology.getCurrent();
        return findArcProto(line, tech);
    }
    
	/**
     * Method to find the ArcProto with the given name.
     * This can be prefixed by a Technology name.
     * @param line the name of the ArcProto.
     * @param tech technology to query
     * @return the specified ArcProto, or null if none can be found.
     */
    public static ArcProto findArcProto(String line, Technology tech) {
        int colon = line.indexOf(':');
        String withoutPrefix;
        if (colon == -1) {
            withoutPrefix = line;
        } else {
            String prefix = line.substring(0, colon);
            Technology t = Technology.findTechnology(prefix);
            if (t != null) {
                tech = t;
            }
            withoutPrefix = line.substring(colon + 1);
        }

        ArcProto ap = tech.findArcProto(withoutPrefix);
        if (ap != null) {
            return ap;
        }
        return null;
    }

    /**
     * Method to return the number of layers that comprise this ArcProto.
     * @return the number of layers that comprise this ArcProto.
     */
    public int getNumArcLayers() {
        return layers.length;
    }

    /**
     * Method to return the list of ArcLayers that comprise this ArcProto..
     * @return the list of ArcLayers that comprise this ArcProto.
     */
    public Technology.ArcLayer[] getArcLayers() {
        return layers;
    }

    /**
     * Method to return layer that comprises by its index in all layers
     * @param arcLayerIndex layer index
     * @return specified layer that comprises this ArcProto.
     */
    public Layer getLayer(int arcLayerIndex) {
        return layers[arcLayerIndex].getLayer();
    }

    /**
     * Returns the extend of specified layer that comprise this ArcProto over base arc width as ECoord object.
     * @param arcLayerIndex layer index
     * @return the extend of specified layer that comprise this ArcProto over base arc width in ECoord object.
     */
    public ECoord getLayerExtend(int arcLayerIndex) {
        return layers[arcLayerIndex].getExtend();
    }

    /**
     * Returns the Poly.Style of specified layer that comprise this ArcLayer.
     * @param arcLayerIndex layer index
     * @return the Poly.Style of specified layer that comprise this ArcLayer.
     */
    public Poly.Type getLayerStyle(int arcLayerIndex) {
        return layers[arcLayerIndex].getStyle();
    }

    /**
     * Returns the extend of specified layer that comprise this ArcProto over base arc width as ECoord object.
     * @param layer specified Layer
     * @return the extend of specified layer that comprise this ArcProto over base arc width as ECoord object.
     * @throws IndexOutOfBoundsException when specified layer diesn't comprise this ArcProto
     */
    public ECoord getLayerExtend(Layer layer) {
        return getLayerExtend(indexOf(layer));
    }

    /**
     * Returns the Poly.Style of specified layer that comprise this ArcLayer.
     * @param layer specified Layer
     * @return the Poly.Style of specified layer that comprise this ArcLayer.
     * @throws IndexOutOfBoundsException when specified layer diesn't comprise this ArcProto
     */
    public Poly.Type getLayerStyle(Layer layer) {
        return getLayerStyle(indexOf(layer));
    }

    /**
     * Method to return specified layer that comprise this ArcProto.
     * @param i layer index
     * @return specified layer that comprise this ArcProto.
     */
    Technology.ArcLayer getArcLayer(int i) {
        return layers[i];
    }

//	/**
//	 * Method to return the array of layers that comprise this ArcProto.
//	 * @return the array of layers that comprise this ArcProto.
//	 */
//	public Iterator<Technology.ArcLayer> getArcLayers() { return ArrayIterator.iterator(layers); }
    /**
     * Method to return an iterator over the layers in this ArcProto.
     * @return an iterator over the layers in this ArcProto.
     */
    public Iterator<Layer> getLayerIterator() {
        return new LayerIterator(layers);
    }

    /**
     * Iterator for Layers on this ArcProto
     */
    private static class LayerIterator implements Iterator<Layer> {

        Technology.ArcLayer[] array;
        int pos;

        public LayerIterator(Technology.ArcLayer[] a) {
            array = a;
            pos = 0;
        }

        @Override
        public boolean hasNext() {
            return pos < array.length;
        }

        @Override
        public Layer next() throws NoSuchElementException {
            if (pos >= array.length) {
                throw new NoSuchElementException();
            }
            return array[pos++].getLayer();
        }

        @Override
        public void remove() throws UnsupportedOperationException, IllegalStateException {
            throw new UnsupportedOperationException();
        }
    }

//	/**
//	 * Method to find the ArcLayer on this ArcProto with a given Layer.
//	 * If there are more than 1 with the given Layer, the first is returned.
//	 * @param layer the Layer to find.
//	 * @return the ArcLayer that has this Layer.
//	 */
//	public Technology.ArcLayer findArcLayer(Layer layer)
//	{
//		for(int j=0; j<layers.length; j++)
//		{
//			Technology.ArcLayer oneLayer = layers[j];
//			if (oneLayer.getLayer() == layer) return oneLayer;
//		}
//		return null;
//	}
    /**
     * Method to find an index of Layer in a list of Layers that comprise this ArcProto.
     * If this layer is not in the list, return -1
     * @param layer the Layer to find.
     * @return an index of Layer in a list of Layers that comprise this ArcProto, or -1.
     */
    public int indexOf(Layer layer) {
        for (int arcLayerIndex = 0; arcLayerIndex < layers.length; arcLayerIndex++) {
            if (layers[arcLayerIndex].getLayer() == layer) {
                return arcLayerIndex;
            }
        }
        return -1;
    }

    /**
     * Method to get MinZ and MaxZ of this ArcProto
     * @param array array[0] is minZ and array[1] is max
     * @return true
     */
    public boolean getZValues(double[] array) 
    {
        for (int j = 0; j < layers.length; j++) {
            Layer layer = layers[j].getLayer();

            double distance = layer.getDistance();
            double thickness = layer.getThickness();
            double z = distance + thickness;

            array[0] = (array[0] > distance) ? distance : array[0];
            array[1] = (array[1] < z) ? z : array[1];
        }
        return true; // always true
    }

    /**
     * Tells if arc can be drawn by simplified algorithm
     * Overidden ins subclasses
     * @param a arc to test
     * @param explain if true then print explanation why arc is not easy
     * @return true if arc can be drawn by simplified algorithm
     */
    public boolean isEasyShape(ImmutableArcInst a, boolean explain) {
        assert a.protoId == getId();
        if (a.isBodyArrowed() || a.isTailArrowed() || a.isHeadArrowed()) {
            if (explain) {
                System.out.println("ARROWED");
            }
            return false;
        }
        if (a.isTailNegated() || a.isHeadNegated()) {
            if (explain) {
                System.out.println("NEGATED");
            }
            return false;
        }
        long minLayerExtend = a.getFixpExtendOverMin() + getMinLayerExtend().getFixp();
        if (minLayerExtend <= 0) {
            if (minLayerExtend != 0 || getNumArcLayers() != 1) {
                if (explain) {
                    System.out.println(this + " many zero-width layers");
                }
                return false;
            }
            return true;
        }
        for (int i = 0, numArcLayers = getNumArcLayers(); i < numArcLayers; i++) {
            if (getLayerStyle(i) != Poly.Type.FILLED) {
                if (explain) {
                    System.out.println("Wide should be filled");
                }
                return false;
            }
        }
        if (!a.isManhattan()) {
            if (explain) {
                System.out.println("NON-MANHATTAN");
            }
            return false;
        }
        return true;
    }

    /**
     * Fill the polygons that describe arc "a".
     * @param b AbstractShapeBuilder to fill polygons.
     * @param a the ImmutableArcInst that is being described.
     */
    protected void getShapeOfArc(AbstractShapeBuilder b, ImmutableArcInst a) {
        getShapeOfArc(b, a, null);
    }

    /**
     * Fill the polygons that describe arc "a".
     * @param b AbstractShapeBuilder to fill polygons.
     * @param a the ImmutableArcInst that is being described.
     * @param graphicsOverride the graphics to use for all generated polygons (if not null).
     */
    protected void getShapeOfArc(AbstractShapeBuilder b, ImmutableArcInst a, EGraphics graphicsOverride) {
        // get information about the arc
        assert a.protoId == getId();
        int numArcLayers = getNumArcLayers();

        // construct the polygons that describe the basic arc
        if (!tech.isNoNegatedArcs() && (a.isHeadNegated() || a.isTailNegated())) {
            for (int i = 0; i < numArcLayers; i++) {
                Technology.ArcLayer primLayer = getArcLayer(i);
                Layer layer = primLayer.getLayer();

                // remove a gap for the negating bubble
                int angle = a.getDefinedAngle();
                double fixpBubbleSize = Schematics.tech().getNegatingBubbleSize() * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE);
                double cosDist = DBMath.cos(angle) * fixpBubbleSize;
                double sinDist = DBMath.sin(angle) * fixpBubbleSize;
                if (!b.skipLayer(layer)) {
                    if (a.isTailNegated()) {
                        b.pushPoint(a.tailLocation, cosDist, sinDist);
                    } else {
                        b.pushPoint(a.tailLocation);
                    }
                    if (a.isHeadNegated()) {
                        b.pushPoint(a.headLocation, -cosDist, -sinDist);
                    } else {
                        b.pushPoint(a.headLocation);
                    }
                    b.pushPoly(Poly.Type.OPENED, layer, graphicsOverride, null);
                }
                Layer node_lay = Schematics.tech().node_lay;
                if (!b.skipLayer(node_lay)) {
                    if (a.isTailNegated()) {
                        b.pushPoint(a.tailLocation, 0.5 * cosDist, 0.5 * sinDist);
                        b.pushPoint(a.tailLocation);
                        b.pushPoly(Poly.Type.CIRCLE, node_lay, null, null);
                    }
                    if (a.isHeadNegated()) {
                        b.pushPoint(a.headLocation, -0.5 * cosDist, -0.5 * sinDist);
                        b.pushPoint(a.headLocation);
                        b.pushPoly(Poly.Type.CIRCLE, node_lay, null, null);
                    }
                }
            }
        } else {
            for (int i = 0; i < numArcLayers; i++) {
                Technology.ArcLayer primLayer = getArcLayer(i);
                Layer layer = primLayer.getLayer();
                if (b.skipLayer(layer)) {
                    continue;
                }
                makeGridPoly(b, a, 2 * (a.getGridExtendOverMin() + getLayerExtend(i).getGrid()), primLayer.getStyle(), layer, graphicsOverride);
            }
        }

        // add an arrow to the arc description
        if (!tech.isNoDirectionalArcs() && !b.skipLayer(tech.generic.glyphLay)) {
            final double fixpArrowSize = 1.0 * (FixpCoord.GRIDS_IN_LAMBDA * FixpCoord.FIXP_SCALE);
            Generic generic = tech.generic;
            int angle = a.getDefinedAngle();
            if (a.isBodyArrowed()) {
                b.pushPoint(a.headLocation);
                b.pushPoint(a.tailLocation);
                b.pushPoly(Poly.Type.VECTORS, generic.glyphLay, null, null);
            }
            if (a.isTailArrowed()) {
                int angleOfArrow = 3300;		// -30 degrees
                int backAngle1 = angle - angleOfArrow;
                int backAngle2 = angle + angleOfArrow;
                b.pushPoint(a.tailLocation);
                b.pushPoint(a.tailLocation, DBMath.cos(backAngle1) * fixpArrowSize, DBMath.sin(backAngle1) * fixpArrowSize);
                b.pushPoint(a.tailLocation);
                b.pushPoint(a.tailLocation, DBMath.cos(backAngle2) * fixpArrowSize, DBMath.sin(backAngle2) * fixpArrowSize);
                b.pushPoly(Poly.Type.VECTORS, generic.glyphLay, null, null);
            }
            if (a.isHeadArrowed()) {
                angle = (angle + 1800) % 3600;
                int angleOfArrow = 300;		// 30 degrees
                int backAngle1 = angle - angleOfArrow;
                int backAngle2 = angle + angleOfArrow;
                b.pushPoint(a.headLocation);
                b.pushPoint(a.headLocation, DBMath.cos(backAngle1) * fixpArrowSize, DBMath.sin(backAngle1) * fixpArrowSize);
                b.pushPoint(a.headLocation);
                b.pushPoint(a.headLocation, DBMath.cos(backAngle2) * fixpArrowSize, DBMath.sin(backAngle2) * fixpArrowSize);
                b.pushPoly(Poly.Type.VECTORS, generic.glyphLay, graphicsOverride, null);
            }
        }
    }

    /**
     * Method to fill in an AbstractShapeBuilder a polygon that describes this ImmutableArcInst in grid units.
     * The polygon is described by its width, and style.
     * @param a the arc information.
     * @param gridWidth the gridWidth of the Poly.
     * @param style the style of the Poly.
     * @param layer layer of the Poly
     * @param graphicsOverride graphics override of the Poly
     */
    public void makeGridPoly(AbstractShapeBuilder b, ImmutableArcInst a, long gridWidth, Poly.Type style, Layer layer, EGraphics graphicsOverride) {
        b.makeGridPoly(a, gridWidth, style, layer, graphicsOverride);
    }
    
    /**
     * Returns the polygons that describe dummy arc of this ArcProto
     * with default width and specified length.
     * @param lambdaLength length of dummy arc in lambda units.
     * @return an array of Poly objects that describes dummy arc graphically.
     * @deprecated Use method with explicit EditingPreferences parameter.
     */
    public Poly[] getShapeOfDummyArc(double lambdaLength) {
        return getShapeOfDummyArc(EditingPreferences.getInstance(), lambdaLength);
    }

    /**
     * Returns the polygons that describe dummy arc of this ArcProto
     * with default width and specified length.
     * @param ep EditingPreferences with default width
     * @param lambdaLength length of dummy arc in lambda units.
     * @return an array of Poly objects that describes dummy arc graphically.
     */
    public Poly[] getShapeOfDummyArc(EditingPreferences ep, double lambdaLength) {
        long l2 = DBMath.lambdaToGrid(lambdaLength / 2);
        // see how many polygons describe this arc
        Poly[] polys = new Poly[layers.length];
//        Point2D.Double headLocation = new Point2D.Double(lambdaLength/2, 0);
//        Point2D.Double tailLocation = new Point2D.Double(-lambdaLength/2, 0);
        long defaultGridExtendOverMin = getDefaultInst(ep).getGridExtendOverMin();
        for (int i = 0; i < layers.length; i++) {
            long gridWidth = 2 * (defaultGridExtendOverMin + getLayerExtend(i).getGrid());
//            long gridWidth = getDefaultGridFullWidth() - primLayer.getGridOffset();
            Poly.Type style = getLayerStyle(i);
            Poly.Point[] points;
            if (gridWidth == 0) {
                points = new Poly.Point[]{Poly.fromGrid(-l2, 0), Poly.fromGrid(l2, 0)};
                if (style == Poly.Type.FILLED) {
                    style = Poly.Type.OPENED;
                }
            } else {
                long w2 = gridWidth / 2;
                assert w2 > 0;
                points = new Poly.Point[]{
                    Poly.fromGrid(-l2 - w2, w2), Poly.fromGrid(-l2 - w2, -w2), Poly.fromGrid(l2 + w2, -w2), Poly.fromGrid(l2 + w2, w2)};
                if (style.isOpened()) {
                    points = new Poly.Point[]{points[0], points[1], points[2], points[3], (Poly.Point) points[0].clone()};
                }
            }
            Poly poly = new Poly(points);
//            poly.gridToLambda();
            poly.setStyle(style);
            poly.setLayer(getLayer(i));
            polys[i] = poly;
        }
        return polys;
    }

    /**
     * Method to describe this ArcProto as a string.
     * Prepends the Technology name if it is
     * not from the current technology (for example, "mocmos:Polysilicon-1").
     * @return a String describing this ArcProto.
     */
    public String describe() {
        String description = "";
        Technology tech = getTechnology();
        if (Technology.getCurrent() != tech) {
            description += tech.getTechName() + ":";
        }
        description += getName();
        return description;
    }

    /**
     * Compares ArcProtos by their Technologies and definition order.
     * @param that the other ArcProto.
     * @return a comparison between the ArcProto.
     */
    @Override
    public int compareTo(ArcProto that) {
        if (this.tech != that.tech) {
            int cmp = this.tech.compareTo(that.tech);
            if (cmp != 0) {
                return cmp;
            }
        }
        return this.primArcIndex - that.primArcIndex;
    }

    /**
     * Method to finish initialization of this ArcProto.
     */
    void finish() {
        if (Double.isNaN(factoryAntennaRatio)) {
            double ratio = ERCAntenna.DEFPOLYRATIO;
            if (function.isMetal()) {
                ratio = ERCAntenna.DEFMETALRATIO;
            }
            setFactoryAntennaRatio(ratio);
        }
//        assert !factoryDefaultInst.isTailArrowed() && !factoryDefaultInst.isHeadArrowed() && !factoryDefaultInst.isBodyArrowed();
        assert factoryDefaultInst.isTailExtended() == factoryDefaultInst.isHeadExtended();
        assert !factoryDefaultInst.isTailNegated() && !factoryDefaultInst.isHeadNegated();
        assert !factoryDefaultInst.isHardSelect();
    }

    /**
     * Returns a printable version of this ArcProto.
     * @return a printable version of this ArcProto.
     */
    @Override
    public String toString() {
        return "arc " + describe();
    }

    void dump(PrintWriter out) {
        out.println("ArcProto " + getName() + " " + getFunction());
        out.println("\tisWipable=" + isWipable());
        out.println("\tisCurvable=" + isCurvable());
        out.println("\tisSpecialArc=" + isSpecialArc());
        out.println("\tisEdgeSelect=" + isEdgeSelect());
        out.println("\tisNotUsed=" + isNotUsed());
        out.println("\tisSkipSizeInPalette=" + isSkipSizeInPalette());

        Technology.printlnPref(out, 1, "DefaultExtendFor" + getName() + "IN" + tech.getTechName(), new Double(factoryDefaultInst.getLambdaExtendOverMin()));
        out.println("\tbaseExtend=" + getBaseExtend());
        out.println("\tdefaultLambdaBaseWidth=" + getFactoryDefaultLambdaBaseWidth());
        out.println("\tdiskOffset1=" + DBMath.round(getBaseExtend().getLambda() + 0.5 * getLambdaElibWidthOffset()));
        out.println("\tdiskOffset2=" + getBaseExtend());
        Technology.printlnPref(out, 1, "DefaultAngleFor" + getName() + "IN" + tech.getTechName(), new Integer(factoryAngleIncrement));
        Technology.printlnPref(out, 1, "DefaultRigidFor" + getName() + "IN" + tech.getTechName(), Boolean.valueOf(factoryDefaultInst.isRigid()));
        Technology.printlnPref(out, 1, "DefaultFixedAngleFor" + getName() + "IN" + tech.getTechName(), Boolean.valueOf(factoryDefaultInst.isFixedAngle()));
        Technology.printlnPref(out, 1, "DefaultExtendedFor" + getName() + "IN" + tech.getTechName(), Boolean.valueOf(factoryDefaultInst.isTailExtended()));
        Technology.printlnPref(out, 1, "DefaultDirectionalFor" + getName() + "IN" + tech.getTechName(), Boolean.valueOf(factoryDefaultInst.isHeadArrowed()));

        for (Technology.ArcLayer arcLayer : layers) {
            arcLayer.dump(out);
        }
    }

    Xml.ArcProto makeXml() {
        Xml.ArcProto a = new Xml.ArcProto();
        a.name = getName();
        for (Map.Entry<String, ArcProto> e : tech.getOldArcNames().entrySet()) {
            if (e.getValue() != this) {
                continue;
            }
            assert a.oldName == null;
            a.oldName = e.getKey();
        }
        a.function = getFunction();
        a.wipable = isWipable();
        a.curvable = isCurvable();
        a.special = isSpecialArc();
        a.notUsed = isNotUsed();
        a.skipSizeInPalette = isSkipSizeInPalette();

        double correction2 = getBaseExtend().getLambda();
        double correction1 = DBMath.round(correction2 + 0.5 * getLambdaElibWidthOffset());
        if (correction1 != correction2) {
            a.diskOffset.put(Integer.valueOf(1), new Double(correction1));
        }
        if (correction2 != 0) {
            a.diskOffset.put(Integer.valueOf(2), new Double(correction2));
        }
        a.extended = factoryDefaultInst.isTailExtended();
        a.fixedAngle = factoryDefaultInst.isFixedAngle();
        a.angleIncrement = getFactoryAngleIncrement();
        a.antennaRatio = getFactoryAntennaRatio();
        for (Technology.ArcLayer arcLayer : layers) {
            a.arcLayers.add(arcLayer.makeXml());
        }
//        if (arcPin != null) {
//            a.arcPin = new Xml.ArcPin();
//            a.arcPin.name = arcPin.getName();
//            PrimitivePort port = arcPin.getPort(0);
//            a.arcPin.portName = port.getName();
//            a.arcPin.elibSize = 2*arcPin.getSizeCorrector(0).getX();
//            for (ArcProto cap: port.getConnections()) {
//                if (cap.getTechnology() == tech && cap != this)
//                    a.arcPin.portArcs.add(cap.getName());
//            }
//
//        }
        return a;
    }

    /**
     * Method to check invariants in this ArcProto.
     * @exception AssertionError if invariants are not valid
     */
    void check() {
        assert protoId.techId == tech.getId();
        for (Technology.ArcLayer primLayer : layers) {
            ECoord extend = getLayerExtend(primLayer.getLayer());
            assert minLayerExtend.compareTo(extend) <= 0 && extend.compareTo(maxLayerExtend) <= 0;
        }
        assert lambdaElibWidthOffset >= 0;
        assert baseExtend.signum() >= 0 && baseExtend.compareTo(maxLayerExtend) <= 0;
        assert baseExtend == getLayerExtend(0);
    }
}
