/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Generic.java
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

import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.EdgeH;
import com.sun.electric.technology.EdgeV;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;

import java.awt.Color;
import java.util.Collections;
import java.util.Iterator;

/**
 * This is the Generic technology.
 */
public class Generic extends Technology {

    /** the Universal Layer. */
    private final Layer universalLay;
    /** the Glyph Layer. */
    public final Layer glyphLay;
    /** the DRC exclusion Layer. */
    public final Layer drcLay;
    /** the Routing exclusion Layer. */
    public final Layer routeLay;
    /** the AFG exclusion Layer. */
    public final Layer afgLay;
    /** the simulation probe Layer. */
    public final Layer simProbeLay;
    /** the Universal Pin node, which connects to every type of arc. */
    public final PrimitiveNode universalPinNode;
    /** the Invisible Pin node, which connects to every type of arc and produces no layout. */
    public final PrimitiveNode invisiblePinNode;
    /** the Unrouted Pin node, for making bends in unrouted arc paths. */
    public final PrimitiveNode unroutedPinNode;
    /** the Cell-Center node, used for defining the origin of the cell's coordinate space. */
    public final PrimitiveNode cellCenterNode;
    /** the Port-definition node, used in technology editing to define node ports. */
    public final PrimitiveNode portNode;
    /** the DRC exclusion node, all design-rule errors covered by this node are ignored. */
    public final PrimitiveNode drcNode;
    /** the Routing exclusion node, routes are disallowed under this (annotation states which layer are excluded). */
    public final PrimitiveNode routeNode;
    /** the AFG exclusion node, tells auto-fill generator to ignore the area. */
    public final PrimitiveNode afgNode;
    /** the Essential-bounds node, used (in pairs) to define the important area of a cell. */
    public final PrimitiveNode essentialBoundsNode;
    /** the Simulation-Probe node, used for highlighting the state of a network. */
    public final PrimitiveNode simProbeNode;
    /** the Universal arc, connects to any node. */
    public final ArcProto universal_arc;
    /** the Invisible arc, connects to any node and produces no layout. */
    public final ArcProto invisible_arc;
    /** the Unrouted arc, connects to any node and specifies desired routing topology. */
    public final ArcProto unrouted_arc;

    /** key of Variable holding routing exclusion layers. */
    public static final Variable.Key ROUTING_EXCLUSION = Variable.newKey("GEN_routing_exclusion");

    /** the Generic Technology object. */
    public static Generic tech() {
        return TechPool.getThreadTechPool().getGeneric();
    }

    // -------------------- private and protected methods ------------------------
    public static Generic newInstance(IdManager idManager) {
        Generic generic = new Generic(idManager);
        generic.setup();
        return generic;
    }

    private Generic(IdManager idManager) {
        super(idManager, null, TechFactory.getGenericFactory(), Collections.<TechFactory.Param, Object>emptyMap(), Foundry.Type.NONE, 0);
        setTechShortName("Generic");
        setTechDesc("Useful primitives");
        setNonStandard();
        setFactoryScale(1000, false);			// in nanometers: really 1 micron

        //**************************************** LAYERS ****************************************

        /** Universal layer */
        universalLay = Layer.newInstance(this, "Universal",
                new EGraphics(false, false, null, 0, 0, 0, 0, 1.0, true,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        universalLay.setFunction(Layer.Function.UNKNOWN);

        /** Invisible layer */
        Layer invisible_lay = Layer.newInstance(this, "Invisible",
                new EGraphics(false, false, null, 0, 180, 180, 180, 1.0, true,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        invisible_lay.setFunction(Layer.Function.UNKNOWN, Layer.Function.NONELEC);

        /** Unrouted layer */
        Layer unrouted_lay = Layer.newInstance(this, "Unrouted",
                new EGraphics(false, false, null, 0, 100, 100, 100, 1.0, true,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        unrouted_lay.setFunction(Layer.Function.UNKNOWN);

        /** Glyph layer */
        glyphLay = Layer.newInstance(this, "Glyph",
                new EGraphics(false, false, null, 0, 0, 0, 0, 1.0, true,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        glyphLay.setFunction(Layer.Function.ART, Layer.Function.NONELEC);

        /** DRC layer */
        drcLay = Layer.newInstance(this, "DRC",
                new EGraphics(false, false, null, 0, 255, 190, 6, 1.0, true,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        drcLay.setFunction(Layer.Function.ART, Layer.Function.NONELEC);

        /** Routing layer */
        routeLay = Layer.newInstance(this, "Route",
            new EGraphics(true, true, null, 0, 255, 86, 6, 0.8, true,
            new int[]{0x8888,	// X   X   X   X  
                0x2222,			//   X   X   X   X 
                0x8888,			// X   X   X   X  
                0x2222,			//   X   X   X   X 
                0x8888,			// X   X   X   X  
                0x2222,			//   X   X   X   X 
                0x8888,			// X   X   X   X  
                0x2222,			//   X   X   X   X 
                0x8888,			// X   X   X   X  
                0x2222,			//   X   X   X   X 
                0x8888,			// X   X   X   X  
                0x2222,			//   X   X   X   X 
                0x8888,			// X   X   X   X  
                0x2222,			//   X   X   X   X 
                0x8888,			// X   X   X   X  
                0x2222}));		//   X   X   X   X 
        routeLay.setFunction(Layer.Function.ART, Layer.Function.NONELEC);

        /** AFG layer */
        afgLay = Layer.newInstance(this, "AFG",
                new EGraphics(false, false, null, 0, 255, 6, 190, 1.0, true,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        afgLay.setFunction(Layer.Function.ART, Layer.Function.NONELEC);

        /** Simulation Probe layer */
        simProbeLay = Layer.newInstance(this, "Sim-Probe",
                new EGraphics(false, false, null, 0, 0, 255, 0, 1.0, true,
                new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        simProbeLay.setFunction(Layer.Function.ART, Layer.Function.NONELEC);

        //**************************************** ARCS ****************************************

        /** Universal arc */
        universal_arc = newArcProto("Universal", 0, 0.0, ArcProto.Function.UNKNOWN,
                new Technology.ArcLayer(universalLay, 0, Poly.Type.FILLED));
        universal_arc.setFactoryFixedAngle(true);
        universal_arc.setFactoryAngleIncrement(45);

        /** Invisible arc */
        invisible_arc = newArcProto("Invisible", 0, 0.0, ArcProto.Function.NONELEC,
                new Technology.ArcLayer(invisible_lay, 0, Poly.Type.FILLED));
        invisible_arc.setFactoryFixedAngle(true);
        invisible_arc.setFactoryAngleIncrement(45);

        /** Unrouted arc */
        unrouted_arc = newArcProto("Unrouted", 0, 0.0, ArcProto.Function.UNROUTED,
                new Technology.ArcLayer(unrouted_lay, 0, Poly.Type.FILLED));
        unrouted_arc.setFactoryFixedAngle(false);
        unrouted_arc.setFactoryAngleIncrement(0);

        //**************************************** NODES ****************************************

        /** Universal pin */
        universalPinNode = PrimitiveNode.newInstance("Universal-Pin", this, 1.0, 1.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(universalLay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.r(0.5), EdgeV.c(0))})
                });
        PrimitivePort univPinPort = PrimitivePort.single(universalPinNode, new ArcProto[]{universal_arc}, "univ", 0, 180, 0, PortCharacteristic.UNKNOWN,
                EdgeH.l(-0.5), EdgeV.b(-0.5), EdgeH.r(0.5), EdgeV.t(0.5));
        universalPinNode.addPrimitivePorts(univPinPort);
        universalPinNode.setFunction(PrimitiveNode.Function.PIN);
        universalPinNode.setWipeOn1or2();
        universalPinNode.setCanBeZeroSize();

        /** Invisible pin */
        invisiblePinNode = new InvisiblePin(invisible_lay);

        /** Unrouted pin */
        unroutedPinNode = PrimitiveNode.newInstance("Unrouted-Pin", this, 1.0, 1.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(unrouted_lay, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.r(0.5), EdgeV.c(0))})
                });
        unroutedPinNode.addPrimitivePorts(
                PrimitivePort.single(unroutedPinNode, new ArcProto[]{unrouted_arc, invisible_arc, universal_arc}, "unrouted", 0, 180, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0)));
        unroutedPinNode.setFunction(PrimitiveNode.Function.PIN);
        unroutedPinNode.setWipeOn1or2();
        unroutedPinNode.setCanBeZeroSize();

        /** Cell Center */
        cellCenterNode = PrimitiveNode.newInstance("Facet-Center", this, 0.0, 0.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(glyphLay, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
                        new Technology.TechPoint(EdgeH.r(0), EdgeV.t(0))
                    }),
                    new Technology.NodeLayer(glyphLay, 0, Poly.Type.BIGCROSS, Technology.NodeLayer.POINTS, Technology.TechPoint.makeCenterBox())
                });
        cellCenterNode.addPrimitivePorts(
                PrimitivePort.single(cellCenterNode, new ArcProto[]{invisible_arc, universal_arc}, "center", 0, 180, 0, PortCharacteristic.UNKNOWN,
                EdgeH.l(0), EdgeV.b(0), EdgeH.r(0), EdgeV.t(0)));
        cellCenterNode.setFunction(PrimitiveNode.Function.ART);
        cellCenterNode.setCanBeZeroSize();

        /** Port */
        portNode = PrimitiveNode.newInstance("Port", this, 6.0, 6.0, ERectangle.fromLambda(-1, -1, 2, 2),
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(glyphLay, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.l(-1), EdgeV.b(-1)),
                        new Technology.TechPoint(EdgeH.r(1), EdgeV.t(1))
                    })
                });
        portNode.addPrimitivePorts(
                PrimitivePort.single(portNode, new ArcProto[]{invisible_arc, universal_arc}, "center", 0, 180, 0, PortCharacteristic.UNKNOWN,
                EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0)));
        portNode.setFunction(PrimitiveNode.Function.ART);
        portNode.setCanBeZeroSize();

        /** Essential Bounds Node */
        essentialBoundsNode = PrimitiveNode.newInstance("Essential-Bounds", this, 0.0, 0.0,
                new Technology.NodeLayer[]{
                    new Technology.NodeLayer(glyphLay, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, new Technology.TechPoint[]{
                        new Technology.TechPoint(EdgeH.c(-1), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
                        new Technology.TechPoint(EdgeH.c(0), EdgeV.c(-1))})
                });
        essentialBoundsNode.addPrimitivePorts(
                PrimitivePort.single(essentialBoundsNode, new ArcProto[]{invisible_arc, universal_arc}, "center", 0, 180, 0, PortCharacteristic.UNKNOWN,
                EdgeH.l(0), EdgeV.b(0), EdgeH.r(0), EdgeV.t(0)));
        essentialBoundsNode.setFunction(PrimitiveNode.Function.ART);
        essentialBoundsNode.setCanBeZeroSize();

        // The pure layer nodes
        drcNode = drcLay.makePureLayerNode("DRC-Node", 2.0, Poly.Type.FILLED, "center", invisible_arc, universal_arc);
        routeNode = routeLay.makePureLayerNode("Route-Node", 2.0, Poly.Type.FILLED, "center", invisible_arc, universal_arc);
        afgNode = afgLay.makePureLayerNode("AFG-Node", 2.0, Poly.Type.FILLED, "center", invisible_arc, universal_arc);
        simProbeNode = simProbeLay.makePureLayerNode("Simulation-Probe", 10.0, Poly.Type.FILLED, "center", invisible_arc, universal_arc);

        // Foundry
        newFoundry(Foundry.Type.NONE, null);

        oldNodeNames.put("Cell-Center", cellCenterNode);
    }

    public void setBackgroudColor(Color c) {
        universalLay.setGraphics(universalLay.getGraphics().withColor(c));
        glyphLay.setGraphics(universalLay.getGraphics().withColor(c));
    }

    private class InvisiblePin extends PrimitiveNode {

        InvisiblePin(Layer invisible_lay) {
            super("Invisible-Pin", Generic.this, EPoint.ORIGIN, 1, 1, ERectangle.ORIGIN,
                    new Technology.NodeLayer[]{
                        new Technology.NodeLayer(invisible_lay, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                            new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
                            new Technology.TechPoint(EdgeH.r(0), EdgeV.t(0))
                        })
                    });
            addPrimitivePorts(PrimitivePort.single(this, new ArcProto[]{invisible_arc, universal_arc}, "center", 0, 180, 0, PortCharacteristic.UNKNOWN,
                    EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0)));
            setFunction(PrimitiveNode.Function.PIN);
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
            if (b.isWipePins() && b.pinUseCount(n)) {
                return;
            }

            Technology.NodeLayer[] primLayers = getNodeLayers();
            boolean hasDisplayVars = false;
            for (Iterator<Variable> it = n.getVariables(); it.hasNext();) {
                Variable var = it.next();
                if (var.isDisplay()) {
                    hasDisplayVars = true;
                }
            }
            if (hasDisplayVars || n.isUsernamed() || b.hasExportsOnNode(n)) {
                return;
            }
            b.genShapeOfNode(n, this, primLayers, null);
        }
    }

    /**
     * Tells if all ArcProtos can connect to the PrimitivePort
     * @param pp PrimitivePort to test
     * @return true if all ArcProtos can connect to the PrimitivePort
     */
    @Override
    public boolean isUniversalConnectivityPort(PrimitivePort pp) {
        PrimitiveNode pn = pp.getParent();
        return pn == universalPinNode || pn == invisiblePinNode || pn == simProbeNode;
    }

    /**
     * Method to detect if this Generic prototype is not relevant for some tool calculation and therefore
     * could be skip. E.g. cellCenter, drcNodes, essential bounds.
     * Similar for layer generation and automatic fill.
     * @param ni the NodeInst in question.
     * @return true if it is a special node (cell center, etc.)
     */
    public static boolean isSpecialGenericNode(NodeInst ni) {
        if (ni.isCellInstance()) {
            return false;
        }
        PrimitiveNode np = (PrimitiveNode) ni.getProto();
        if (!(np.getTechnology() instanceof Generic)) {
            return false;
        }
        Generic tech = (Generic) np.getTechnology();
        return (np == tech.cellCenterNode || np == tech.drcNode || np == tech.routeNode ||
        	np == tech.essentialBoundsNode || np == tech.afgNode);
    }
    
    /**
     * Method to check if prototype associated with NodeInst is the cell center.
     * Useful to avoid rotating the center in Cell -> Rotate.
     * This function is much faster than isSpecialGenericNode since we don't
     * check if NodeInst is a cell or the technology is Generic
     * @param ni the NodeInst in question
     * @return true if it is the cell center.
     */
    public static boolean isCellCenter(NodeInst ni)
    {
    	return ni.getProto() == Generic.tech().cellCenterNode;
    }
    
    /**
     * Method to check if prototype associated with NodeInst is an essential bounds.
     * @param ni the NodeInst in question.
     * @return true if it is an essential bounds.
     */
    public static boolean isEssentialBnd(NodeInst ni)
    {
    	return ni.getProto() == Generic.tech().essentialBoundsNode;
    }
    
    /**
     * Method to check if prototype associated with NodeInst is either the
     * cell center or an essential bounds.
     * @param ni the NodeInst in question.
     * @return true if it is a cell center or essential bounds.
     */
    public static boolean isCellCenterOrEssentialBnd(NodeInst ni)
    {
    	return isCellCenter(ni) || isEssentialBnd(ni);
    }
}
