/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Artwork.java
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

import com.sun.electric.database.CellBackup;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableElectricObject;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.BoundsBuilder;
import com.sun.electric.technology.EdgeH;
import com.sun.electric.technology.EdgeV;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;

import java.awt.geom.Point2D;

/**
 * This is the general purpose sketching technology.
 */
public class Artwork extends Technology {

    /**
     * Key of Variable holding starting and ending angles.
     * As a special case, NodeInst.checkPossibleVariableEffects()
     * updates the node when this variable changes.
     */
    public static final Variable.Key ART_DEGREES = Variable.newKey("ART_degrees");
    /** key of Variable holding message text. */
    public static final Variable.Key ART_MESSAGE = Variable.newKey("ART_message");
    /** key of Variable holding color information */
    public static final Variable.Key ART_COLOR = Variable.newKey("ART_color");
    /** key of Variable holding color information */
    public static final Variable.Key ART_PATTERN = Variable.newKey("ART_pattern");

    /** the Artwork Technology object. */
    public static Artwork tech() {
        return TechPool.getThreadTechPool().getArtwork();
    }
    /** number of lines in an ellipse */
    private static final int ELLIPSEPOINTS = 30;
    /** granularity of a spline */
    private static final int SPLINEGRAIN = 20;
    /** Defines a Pin node. */
    public final PrimitiveNode pinNode;
    /** Defines a Box node. */
    public final PrimitiveNode boxNode;
    /** Defines a Crossed-Box node. */
    public final PrimitiveNode crossedBoxNode;
    /** Defines a Filled-Box node. */
    public final PrimitiveNode filledBoxNode;
    /** Defines a Circle node. */
    public final PrimitiveNode circleNode;
    /** Defines a Filled-Circle node. */
    public final PrimitiveNode filledCircleNode;
    /** Defines a Spline node. */
    public final PrimitiveNode splineNode;
    /** Defines a Triangle node. */
    public final PrimitiveNode triangleNode;
    /** Defines a Filled-Triangle node. */
    public final PrimitiveNode filledTriangleNode;
    /** Defines a Arrow node. */
    public final PrimitiveNode arrowNode;
    /** Defines a Opened-Polygon node. */
    public final PrimitiveNode openedPolygonNode;
    /** Defines a Opened-Dotted-Polygon node. */
    public final PrimitiveNode openedDottedPolygonNode;
    /** Defines a Opened-Dashed-Polygon node. */
    public final PrimitiveNode openedDashedPolygonNode;
    /** Defines a Opened-Thicker-Polygon node. */
    public final PrimitiveNode openedThickerPolygonNode;
    /** Defines a Closed-Polygon node. */
    public final PrimitiveNode closedPolygonNode;
    /** Defines a Filled-Polygon node. */
    public final PrimitiveNode filledPolygonNode;
    /** Defines a Thick-Circle node. */
    public final PrimitiveNode thickCircleNode;
    /** Defines a Solid arc. */
    public final ArcProto solidArc;
    /** Defines a Dotted arc. */
    public final ArcProto dottedArc;
    /** Defines a Dashed arc. */
    public final ArcProto dashedArc;
    /** Defines a Thick arc. */
    public final ArcProto thickerArc;
    /** the layer */
    public final Layer defaultLayer;

    // -------------------- private and protected methods ------------------------
    public Artwork(Generic generic, TechFactory techFactory) {
        super(generic, techFactory);
        setTechShortName("Artwork");
        setTechDesc("General-purpose artwork components");
        setFactoryScale(2000, false);			// in nanometers: really 2 micron
        setNonStandard();
        setNonElectrical();
        setNoNegatedArcs();
        setStaticTechnology();

        //**************************************** LAYERS ****************************************

        /** Graphics layer */
        defaultLayer = Layer.newInstance(this, "Graphics",
                new EGraphics(false, false, null, 0, 0, 0, 0, 0.8, true,
                new int[]{0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff,
                    0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff, 0xffff}));

        // The layer functions
        defaultLayer.setFunction(Layer.Function.ART, Layer.Function.NONELEC);		// Graphics

        // The DXF names
        defaultLayer.setFactoryDXFLayer("OBJECT");		// Graphics

        //******************** ARCS ********************

        /** Solid arc */
        solidArc = new ArtworkArcProto("Solid", Poly.Type.FILLED);
        /** Dotted arc */
        dottedArc = new ArtworkArcProto("Dotted", Poly.Type.OPENEDT1);
        /** Dashed arc */
        dashedArc = new ArtworkArcProto("Dashed", Poly.Type.OPENEDT2);
        /** Thicker arc */
        thickerArc = new ArtworkArcProto("Thicker", Poly.Type.OPENEDT3);

        //******************** RECTANGLE DESCRIPTIONS ********************

        Technology.TechPoint[] box_1 = new Technology.TechPoint[]{
            new Technology.TechPoint(EdgeH.l(0), EdgeV.c(0)),
            new Technology.TechPoint(EdgeH.c(0), EdgeV.t(0)),
            new Technology.TechPoint(EdgeH.r(0), EdgeV.b(0)),
            new Technology.TechPoint(EdgeH.c(0), EdgeV.b(0)),};
        Technology.TechPoint[] box_2 = new Technology.TechPoint[]{
            new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
            new Technology.TechPoint(new EdgeH(-0.125, 0), EdgeV.t(0)),
            new Technology.TechPoint(new EdgeH(0.125, 0), EdgeV.b(0)),
            new Technology.TechPoint(EdgeH.r(0), EdgeV.t(0)),};
        Technology.TechPoint[] box_4 = new Technology.TechPoint[]{
            new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
            new Technology.TechPoint(EdgeH.r(0), EdgeV.b(0)),
            new Technology.TechPoint(EdgeH.c(0), EdgeV.t(0)),};
        Technology.TechPoint[] box_6 = new Technology.TechPoint[]{
            new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),
            new Technology.TechPoint(EdgeH.r(0), EdgeV.c(0)),};

        //******************** NODES ********************

        /** Pin */
        pinNode = new ArtworkNode("Pin", 1, 1,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, box_6));
        pinNode.addPrimitivePorts(
                new ArtworkPrimitivePort(pinNode, "site", 0, 180, EdgeH.c(0), EdgeV.c(0), EdgeH.c(0), EdgeV.c(0)));
        pinNode.setFunction(PrimitiveNode.Function.PIN);
        pinNode.setArcsWipe();
        pinNode.setArcsShrink();

        /** Box */
        boxNode = new ArtworkNode("Box", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.CLOSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
                    new Technology.TechPoint(EdgeH.r(0), EdgeV.t(0))
                }));
        boxNode.addPrimitivePorts(new NodeShapedPort(boxNode, "box", 0, 180));
        boxNode.setFunction(PrimitiveNode.Function.ART);
        boxNode.setEdgeSelect();

        /** Crossed-Box */
        crossedBoxNode = new ArtworkNode("Crossed-Box", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.CROSSED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
                    new Technology.TechPoint(EdgeH.r(0), EdgeV.t(0))
                }));
        crossedBoxNode.addPrimitivePorts(new NodeShapedPort(crossedBoxNode, "fbox", 0, 180));
        crossedBoxNode.setFunction(PrimitiveNode.Function.ART);

        /** Filled-Box */
        filledBoxNode = new ArtworkNode("Filled-Box", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.FILLED, Technology.NodeLayer.BOX, new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
                    new Technology.TechPoint(EdgeH.r(0), EdgeV.t(0))
                }));
        filledBoxNode.addPrimitivePorts(new NodeShapedPort(filledBoxNode, "fbox", 0, 180));
        filledBoxNode.setFunction(PrimitiveNode.Function.ART);
        filledBoxNode.setEdgeSelect();

        /** Circle */
        circleNode = new ArtworkNode("Circle", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.CIRCLE, Technology.NodeLayer.POINTS, box_6));
        circleNode.addPrimitivePorts(
                new ArtworkPrimitivePort(circleNode, "site", 0, 180, EdgeH.l(0), EdgeV.b(0), EdgeH.r(0), EdgeV.t(0)));
        circleNode.setFunction(PrimitiveNode.Function.ART);
        circleNode.setEdgeSelect();
        circleNode.setPartialCircle();

        /** Filled-Circle */
        filledCircleNode = new ArtworkNode("Filled-Circle", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.DISC, Technology.NodeLayer.POINTS, box_6));
        filledCircleNode.addPrimitivePorts(
                new ArtworkPrimitivePort(filledCircleNode, "site", 0, 180, EdgeH.l(0), EdgeV.b(0), EdgeH.r(0), EdgeV.t(0)));
        filledCircleNode.setFunction(PrimitiveNode.Function.ART);
        filledCircleNode.setSquare();
        filledCircleNode.setEdgeSelect();

        /** Spline */
        splineNode = new ArtworkNode("Spline", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, box_2));
        PrimitivePort splinePort =
                new NodeShapedPort(splineNode, "site", 0, 180) {

                    @Override
                    protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
                        EPoint[] tracePoints = n.getTrace();
                        if (tracePoints != null) {
                            Poly.Point[] pointList = fillSpline(EPoint.ORIGIN, tracePoints);
                            for (Poly.Point p : pointList) {
                                b.pushPoint(p.getFixpX(), p.getFixpY());
                            }
                            b.pushPoly(Poly.Type.OPENED, null, null, null);
                            return;
                        }
                        super.genShape(b, n);
                    }
                };
        splineNode.addPrimitivePorts(splinePort);
        splineNode.setFunction(PrimitiveNode.Function.ART);
        splineNode.setHoldsOutline();
        splineNode.setEdgeSelect();

        /** Triangle */
        triangleNode = new ArtworkNode("Triangle", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, box_4));
        triangleNode.addPrimitivePorts(new NodeShapedPort(triangleNode, "triangle", 0, 180));
        triangleNode.setFunction(PrimitiveNode.Function.ART);
        triangleNode.setEdgeSelect();

        /** Filled-Triangle */
        filledTriangleNode = new ArtworkNode("Filled-Triangle", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.FILLED, Technology.NodeLayer.POINTS, box_4));
        filledTriangleNode.addPrimitivePorts(new NodeShapedPort(filledTriangleNode, "ftriangle", 0, 180));
        filledTriangleNode.setFunction(PrimitiveNode.Function.ART);
        filledTriangleNode.setEdgeSelect();

        /** Arrow */
        arrowNode = new ArtworkNode("Arrow", 2, 2,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.FILLED, Technology.NodeLayer.POINTS,
                new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(0), EdgeV.t(0)),
                    new Technology.TechPoint(EdgeH.r(0), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),}),
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.FILLED, Technology.NodeLayer.POINTS,
                new Technology.TechPoint[]{
                    new Technology.TechPoint(EdgeH.l(0), EdgeV.b(0)),
                    new Technology.TechPoint(EdgeH.r(0), EdgeV.c(0)),
                    new Technology.TechPoint(EdgeH.c(0), EdgeV.c(0)),}) //				new Technology.NodeLayer(defaultLayer, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS,
                //					new Technology.TechPoint[]
                //					{
                //						new Technology.TechPoint(EdgeH.makeLeftEdge(), EdgeV.makeTopEdge()),
                //						new Technology.TechPoint(EdgeH.makeRightEdge(), EdgeV.makeCenter()),
                //						new Technology.TechPoint(EdgeH.makeLeftEdge(), EdgeV.makeBottomEdge()),
                //					})
                );
        arrowNode.addPrimitivePorts(
                new ArtworkPrimitivePort(arrowNode, "arrow", 0, 180, EdgeH.r(0), EdgeV.c(0), EdgeH.r(0), EdgeV.c(0)));
        arrowNode.setFunction(PrimitiveNode.Function.ART);
        arrowNode.setEdgeSelect();

        /** Opened-Polygon */
        openedPolygonNode = new ArtworkNode("Opened-Polygon", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.OPENED, Technology.NodeLayer.POINTS, box_2));
        openedPolygonNode.addPrimitivePorts(new NodeShapedPort(openedPolygonNode, "site", 0, 180));
        openedPolygonNode.setFunction(PrimitiveNode.Function.ART);
        openedPolygonNode.setHoldsOutline();
        openedPolygonNode.setEdgeSelect();

        /** Opened-Dotted-Polygon */
        openedDottedPolygonNode = new ArtworkNode("Opened-Dotted-Polygon", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.OPENEDT1, Technology.NodeLayer.POINTS, box_2));
        openedDottedPolygonNode.addPrimitivePorts(new NodeShapedPort(openedDottedPolygonNode, "site", 0, 180));
        openedDottedPolygonNode.setFunction(PrimitiveNode.Function.ART);
        openedDottedPolygonNode.setHoldsOutline();
        openedDottedPolygonNode.setEdgeSelect();

        /** Opened-Dashed-Polygon */
        openedDashedPolygonNode = new ArtworkNode("Opened-Dashed-Polygon", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.OPENEDT2, Technology.NodeLayer.POINTS, box_2));
        openedDashedPolygonNode.addPrimitivePorts(new NodeShapedPort(openedDashedPolygonNode, "site", 0, 180));
        openedDashedPolygonNode.setFunction(PrimitiveNode.Function.ART);
        openedDashedPolygonNode.setHoldsOutline();
        openedDashedPolygonNode.setEdgeSelect();

        /** Opened-Thicker-Polygon */
        openedThickerPolygonNode = new ArtworkNode("Opened-Thicker-Polygon", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.OPENEDT3, Technology.NodeLayer.POINTS, box_2));
        openedThickerPolygonNode.addPrimitivePorts(new NodeShapedPort(openedThickerPolygonNode, "site", 0, 180));
        openedThickerPolygonNode.setFunction(PrimitiveNode.Function.ART);
        openedThickerPolygonNode.setHoldsOutline();
        openedThickerPolygonNode.setEdgeSelect();

        /** Closed-Polygon */
        closedPolygonNode = new ArtworkNode("Closed-Polygon", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.CLOSED, Technology.NodeLayer.POINTS, box_1));
        closedPolygonNode.addPrimitivePorts(new NodeShapedPort(closedPolygonNode, "site", 0, 180));
        closedPolygonNode.setFunction(PrimitiveNode.Function.ART);
        closedPolygonNode.setHoldsOutline();
        closedPolygonNode.setEdgeSelect();

        /** Filled-Polygon */
        filledPolygonNode = new ArtworkNode("Filled-Polygon", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.FILLED, Technology.NodeLayer.POINTS, box_1));
        filledPolygonNode.addPrimitivePorts(new NodeShapedPort(filledPolygonNode, "site", 0, 180));
        filledPolygonNode.setFunction(PrimitiveNode.Function.ART);
        filledPolygonNode.setHoldsOutline();
        filledPolygonNode.setEdgeSelect();

        /** Thick-Circle */
        thickCircleNode = new ArtworkNode("Thick-Circle", 6, 6,
                new Technology.NodeLayer(defaultLayer, 0, Poly.Type.THICKCIRCLE, Technology.NodeLayer.POINTS, box_6));
        thickCircleNode.addPrimitivePorts(
                new ArtworkPrimitivePort(thickCircleNode, "site", 0, 180, EdgeH.l(0), EdgeV.b(0), EdgeH.r(0), EdgeV.t(0)));
        thickCircleNode.setFunction(PrimitiveNode.Function.ART);
        thickCircleNode.setEdgeSelect();
        thickCircleNode.setPartialCircle();

        // Foundry
        newFoundry(Foundry.Type.NONE, null,
                // The GDS names
                "Graphics 1");
//		noFoundry.setFactoryGDSLayer(defaultLayer, "1");
//		defaultLayer.setFactoryGDSLayer("1", Foundry.Type.MOSIS.name());		// Graphics

        oldArcNames.put("Dash-1", dottedArc);
        oldArcNames.put("Dash-2", dashedArc);
        oldArcNames.put("Dash-3", thickerArc);

        oldNodeNames.put("Message", generic.invisiblePinNode);
        oldNodeNames.put("Centered-Message", generic.invisiblePinNode);
        oldNodeNames.put("Left-Message", generic.invisiblePinNode);
        oldNodeNames.put("Right-Message", generic.invisiblePinNode);
        oldNodeNames.put("Opened-FarDotted-Polygon", openedThickerPolygonNode);

        loadFactoryMenuPalette(Artwork.class.getResource("artworkMenu.xml"));
    }

    private class ArtworkNode extends PrimitiveNode {

        private ArtworkNode(String protoName, double width, double height, Technology.NodeLayer... layers) {
            super(protoName, Artwork.this, EPoint.ORIGIN, width, height, ERectangle.ORIGIN, layers);
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
            if (b.isWipePins()) {
                if (isArcsWipe() && b.isWiped(n)) {
                    return;
                }
                assert !isWipeOn1or2();
            }

            if (b.skipLayer(defaultLayer)) {
                return;
            }
            genShape(b, n, makeGraphics(n));
        }

        private void genShape(AbstractShapeBuilder b, ImmutableNodeInst n, EGraphics graphicsOverride) {
            Technology.NodeLayer[] primLayers = getNodeLayers();
        	if (isPartialCircle()) {
                double[] angles = n.getArcDegrees();
                if (n.size.getGridX() != n.size.getGridY()) {
                    // handle ellipses
                    Poly.Point[] pointList = fillEllipse(EPoint.ORIGIN, n.size.getLambdaX(), n.size.getLambdaY(),
                            angles[0], angles[1]);
                    b.setCurNode(n);
                    for (Poly.Point p : pointList) {
                        b.pushPoint(p.getFixpX(), p.getFixpY());
                    }
                    Poly.Type style = this == circleNode ? Poly.Type.OPENED : Poly.Type.OPENEDT3;
                    b.pushPoly(style, defaultLayer, graphicsOverride, null);
                    return;
                }

                // if there is arc information here, make it an arc of a circle
                if (angles[0] != 0.0 || angles[1] != 0.0) {
                    // fill an arc of a circle here
                    double dist = n.size.getFixpX() * 0.5;
                    b.setCurNode(n);
                    b.pushPoint(EPoint.ORIGIN);
                    b.pushPoint(Math.cos(angles[0] + angles[1]) * dist, Math.sin(angles[0] + angles[1]) * dist);
                    b.pushPoint(Math.cos(angles[0]) * dist, Math.sin(angles[0]) * dist);
                    Poly.Type style = this == circleNode ? Poly.Type.CIRCLEARC : Poly.Type.THICKCIRCLEARC;
                    b.pushPoly(style, defaultLayer, graphicsOverride, null);
                    return;
                }
            } else if (this == splineNode) {
                EPoint[] tracePoints = n.getTrace();
                if (tracePoints != null) {
                    Poly.Point[] pointList = fillSpline(EPoint.ORIGIN, tracePoints);
                    b.setCurNode(n);
                    for (Poly.Point p : pointList) {
                        b.pushPoint(p.getFixpX(), p.getFixpY());
                    }
                    b.pushPoly(Poly.Type.OPENED, defaultLayer, graphicsOverride, null);
                    return;
                }
            }

            // Pure-layer nodes and serpentine trans may have outline trace
            if (isHoldsOutline()) {
                EPoint[] outline = n.getTrace();
                if (outline != null) {
                    assert primLayers.length == 1;
                    Technology.NodeLayer primLayer = primLayers[0];
                    Layer layer = primLayer.getLayer();
                    if (b.skipLayer(layer)) {
                        return;
                    }
                    Poly.Type style = primLayer.getStyle();
                    boolean removeCoincidentPoints;
                    boolean removeSameStartEnd;
                    switch (style) {
                        case FILLED:
                        case CLOSED:
                            removeCoincidentPoints = true;
                            removeSameStartEnd = true;
                            break;
                        case OPENED:
                        case OPENEDT1:
                        case OPENEDT2:
                        case OPENEDT3:
                            removeCoincidentPoints = true;
                            removeSameStartEnd = false;
                        default:
                            removeCoincidentPoints = false;
                            removeSameStartEnd = false;
                    }
                    PrimitivePort pp = primLayer.getPort(this);
                    b.setCurNode(n);
                    int startPoint = 0;
                    for (int i = 1; i < outline.length; i++) {
                        boolean breakPoint = (i == outline.length - 1) || (outline[i] == null);
                        if (breakPoint) {
                            if (i == outline.length - 1) {
                                i++;
                            }
                            b.pushOutlineSegment(outline, startPoint, i - startPoint,
                                    removeCoincidentPoints, removeSameStartEnd);
                            b.pushPoly(style, layer, graphicsOverride, pp);
                            startPoint = i + 1;
                        }
                    }
                    return;
                }
            }

            b.genShapeOfNode(n, this, primLayers, graphicsOverride);
        }

        @Override
        public void genBounds(ImmutableNodeInst n, long[] gridCoords) {
            // special case for arcs of circles
        	if (isPartialCircle()) {
                // see if this circle is only a partial one
                double[] angles = n.getArcDegrees();
                if (angles[0] != 0.0 || angles[1] != 0.0) {
                    genBoundsHard(null, n, gridCoords);
                    return;
                }
            }
            if (n.getTrace() != null) {
                genBoundsHard(null, n, gridCoords);
                return;
            }
            super.genBounds(n, gridCoords);
        }

        @Override
        public void genElibBounds(CellBackup cellBackup, ImmutableNodeInst n, long[] gridCoords) {
            // special case for arcs of circles
        	if (isPartialCircle()) {
                // see if this circle is only a partial one
                double[] angles = n.getArcDegrees();
                if (angles[0] != 0.0 || angles[1] != 0.0) {
                    genBoundsHard(cellBackup, n, gridCoords);
                    return;
                }
            }
            if (n.getTrace() != null) {
                genBoundsHard(cellBackup, n, gridCoords);
                return;
            }
            super.genBounds(n, gridCoords);
        }

        private void genBoundsHard(CellBackup cellBackup, ImmutableNodeInst n, long[] gridCoords) {
            BoundsBuilder b = cellBackup != null ? new BoundsBuilder(cellBackup) : new BoundsBuilder((TechPool) null);
            genShape(b, n);
            ERectangle bounds = b.makeBounds();
            gridCoords[0] = bounds.getGridMinX();
            gridCoords[1] = bounds.getGridMinY();
            gridCoords[2] = bounds.getGridMaxX();
            gridCoords[3] = bounds.getGridMaxY();
        }
    }

    private class NodeShapedPort extends ArtworkPrimitivePort {

        private NodeShapedPort(PrimitiveNode parent, String protoName, int portAngle, int portRange) {
            super(parent, protoName, portAngle, portRange, EdgeH.l(0), EdgeV.b(0), EdgeH.r(0), EdgeV.t(0));
        }

        @Override
        protected void genShape(AbstractShapeBuilder b, ImmutableNodeInst n) {
            ((ArtworkNode) getParent()).genShape(b, n, null);
        }
    }

    private class ArtworkPrimitivePort extends PrimitivePort {

        private ArtworkPrimitivePort(PrimitiveNode parent, String protoName, int portAngle, int portRange,
                EdgeH left, EdgeV bottom, EdgeH right, EdgeV top) {
            super(parent, new ArcProto[]{solidArc, dottedArc, dashedArc, thickerArc},
                    protoName, true, portAngle, portRange, 0, PortCharacteristic.UNKNOWN, false, false,
                    left, bottom, right, top);
        }
    }

    private class ArtworkArcProto extends ArcProto.Curvable {

        private ArtworkArcProto(String protoName, Poly.Type type) {
            super(Artwork.this, protoName, 0, ArcProto.Function.NONELEC,
                    new Technology.ArcLayer[]{new Technology.ArcLayer(defaultLayer, 0, type)},
                    getNumArcs());
            setFactoryFixedAngle(false);
            setWipable();
            setFactoryAngleIncrement(0);
            addArcProto(this);
        }

        /**
         * Tells if arc can be drawn by simplified algorithm
         * Arcs with user-specified color or pattern are not easy
         * @param a arc to test
         * @param explain if true then print explanation why arc is not easy
         * @return true if arc can be drawn by simplified algorithm
         */
        @Override
        public boolean isEasyShape(ImmutableArcInst a, boolean explain) {
            if (a.getVar(Artwork.ART_COLOR) != null) {
                if (explain) {
                    System.out.println("ART_COLOR");
                }
                return false;
            }
            if (a.getVar(Artwork.ART_PATTERN) != null) {
                if (explain) {
                    System.out.println("ART_PATTERN");
                }
                return false;
            }
            return super.isEasyShape(a, explain);
        }

        /**
         * Fill the polygons that describe arc "a".
         * @param b AbstractShapeBuilder to fill polygons.
         * @param a the ImmutableArcInst that is being described.
         */
        @Override
        protected void getShapeOfArc(AbstractShapeBuilder b, ImmutableArcInst a) {
            getShapeOfArc(b, a, makeGraphics(a));
        }
    }

    /**
     * Method to return an array of Point2D that describe an ellipse.
     * @param center the center coordinate of the ellipse.
     * @param sX the X size of the ellipse.
     * @param sY the Y size of the ellipse.
     * @param startoffset the starting angle of the ellipse, in radians.
     * @param endangle the ending angle of the ellipse, in radians.
     * If both startoffset and endangle are zero, draw the full ellipse.
     * @return an array of points that describes the ellipse.
     */
    public static Poly.Point[] fillEllipse(Point2D center, double sX, double sY, double startoffset, double endangle) {
        // ensure that the polygon can hold the vectors
        boolean closed = true;
        if (startoffset == 0 && endangle == 0) {
            // full ellipse
            endangle = Math.PI * 2.0;
        } else {
            // partial ellipse
            closed = false;
        }
        int pts = (int) (endangle * ELLIPSEPOINTS / (Math.PI * 2.0));
        if (pts < 3) {
            pts = 3;
        }
        if (closed) {
            pts++;
        }

        Poly.Point[] points = new Poly.Point[pts];

        // compute the length of the semi-major and semi-minor axes
        double a = sX / 2;
        double b = sY / 2;

        if (closed) {
            // more efficient algorithm used for full ellipse drawing
            double p = 2.0 * Math.PI / (ELLIPSEPOINTS - 1);
            double c2 = Math.cos(p);
            double s2 = Math.sin(p);
            double c3 = 1.0;
            double s3 = 0.0;
            for (int m = 0; m < ELLIPSEPOINTS; m++) {
                points[m] = Poly.fromLambda(center.getX() + a * c3, center.getY() + b * s3);
                double t1 = c3 * c2 - s3 * s2;
                s3 = s3 * c2 + c3 * s2;
                c3 = t1;
            }
        } else {
            // less efficient algorithm for partial ellipse drawing
            for (int m = 0; m < pts; m++) {
                double p = startoffset + m * endangle / (pts - 1);
                double c2 = Math.cos(p);
                double s2 = Math.sin(p);
                points[m] = Poly.fromLambda(center.getX() + a * c2, center.getY() + b * s2);
            }
        }
        return points;
    }

    /**
     * Method to extract an X coordinate from an array.
     * @param tracePoints the array of coordinate values.
     * @param index the entry in the array to retrieve.
     * @param cX an offset value to add to the retrieved value.
     * @return the X coordinate value.
     */
    private double getTracePointX(Point2D[] tracePoints, int index, double cX) {
        double v = tracePoints[index].getX();
        return v + cX;
    }

    /**
     * Method to extract an Y coordinate from an array.
     * @param tracePoints the array of coordinate values.
     * @param index the entry in the array to retrieve.
     * @param cY an offset value to add to the retrieved value.
     * @return the Y coordinate value.
     */
    private double getTracePointY(Point2D[] tracePoints, int index, double cY) {
        double v = tracePoints[index].getY();
        return v + cY;
    }

    /**
     * Method to set default outline information on a NodeInst.
     * Very few primitives have default outline information (usually just in the Artwork Technology).
     * This method overrides the one in Technology.
     * @param ni the NodeInst to load with default outline information.
     */
    @Override
    public void setDefaultOutline(NodeInst ni) {
        if (ni.isCellInstance()) {
            return;
        }
        PrimitiveNode np = (PrimitiveNode) ni.getProto();
        double x = ni.getAnchorCenterX();
        double y = ni.getAnchorCenterY();
        if (np == openedPolygonNode || np == openedDottedPolygonNode
                || np == openedDashedPolygonNode || np == openedThickerPolygonNode
                || np == splineNode) {
            EPoint[] outline = new EPoint[4];
            outline[0] = EPoint.fromLambda(x - 3, y - 3);
            outline[1] = EPoint.fromLambda(x - 1, y + 3);
            outline[2] = EPoint.fromLambda(x + 1, y - 3);
            outline[3] = EPoint.fromLambda(x + 3, y + 3);
            ni.setTrace(outline);
        }
        if (np == closedPolygonNode || np == filledPolygonNode) {
            Point2D[] outline = new EPoint[4];
            outline[0] = EPoint.fromLambda(x + 0, y - 3);
            outline[1] = EPoint.fromLambda(x - 3, y + 0);
            outline[2] = EPoint.fromLambda(x + 0, y + 3);
            outline[3] = EPoint.fromLambda(x + 3, y - 3);
            ni.setTrace(outline);
        }
    }

    /**
     * Method to convert the given spline control points into a spline curve.
     * @param c the center of the spline.
     * @param tracePoints the array of control point values, alternating X/Y/X/Y.
     * @return an array of points that describes the spline.
     */
    public Poly.Point[] fillSpline(EPoint c, EPoint[] tracePoints) {
        double cX = c.getLambdaX();
        double cY = c.getLambdaY();
        int steps = SPLINEGRAIN;
        int count = tracePoints.length;
        int outPoints = (count - 1) * steps + 1;
        Poly.Point[] points = new Poly.Point[outPoints];
        int out = 0;

        double splineStep = 1.0 / steps;
        double x2 = getTracePointX(tracePoints, 0, cX) * 2 - getTracePointX(tracePoints, 1, cX);
        double y2 = getTracePointY(tracePoints, 0, cY) * 2 - getTracePointY(tracePoints, 1, cY);
        double x3 = getTracePointX(tracePoints, 0, cX);
        double y3 = getTracePointY(tracePoints, 0, cY);
        double x4 = getTracePointX(tracePoints, 1, cX);
        double y4 = getTracePointY(tracePoints, 1, cY);
        for (int k = 2; k <= count; k++) {
            double x1 = x2;
            x2 = x3;
            x3 = x4;
            double y1 = y2;
            y2 = y3;
            y3 = y4;
            if (k == count) {
                x4 = getTracePointX(tracePoints, k - 1, cX) * 2 - getTracePointX(tracePoints, k - 2, cX);
                y4 = getTracePointY(tracePoints, k - 1, cY) * 2 - getTracePointY(tracePoints, k - 2, cY);
            } else {
                x4 = getTracePointX(tracePoints, k, cX);
                y4 = getTracePointY(tracePoints, k, cY);
            }

            int i = 0;
            for (double t = 0.0; i < steps; i++, t += splineStep) {
                double tsq = t * t;
                double t4 = tsq * t;
                double t3 = -3.0 * t4 + 3.0 * tsq + 3.0 * t + 1.0;
                double t2 = 3.0 * t4 - 6.0 * tsq + 4.0;
                double t1 = -t4 + 3.0 * tsq - 3.0 * t + 1.0;

                double x = (x1 * t1 + x2 * t2 + x3 * t3 + x4 * t4) / 6.0;
                double y = (y1 * t1 + y2 * t2 + y3 * t3 + y4 * t4) / 6.0;
                points[out++] = Poly.fromLambda(x, y);
            }
        }

        // close the spline
        points[out++] = Poly.fromLambda(getTracePointX(tracePoints, count - 1, cX),
                getTracePointY(tracePoints, count - 1, cY));
        return points;
    }

    /**
     * Method to create an EGraphics for an ElectricObject with color and pattern Variables.
     * @param eObj the ElectricObject with graphics specifications.
     * @return a new EGraphics that has the color and pattern.
     */
    public EGraphics makeGraphics(ElectricObject eObj) {
        return makeGraphics(eObj.getD());
    }

    /**
     * Method to create an EGraphics for an ImmutableElectricObject with color and pattern Variables.
     * @param d the ImmutableElectricObject with graphics specifications.
     * @return a new EGraphics that has the color and pattern.
     */
    private EGraphics makeGraphics(ImmutableElectricObject d) {
        // get the color and pattern information
        Integer color = d.getVarValue(ART_COLOR, Integer.class);
        Variable patternVar = d.getVar(ART_PATTERN);
        if (color == null && patternVar == null) {
            return null;
        }

        // make a fake layer with graphics
        EGraphics graphics = defaultLayer.getFactoryGraphics();

        // set the color if specified
        if (color != null) {
            graphics = graphics.withColorIndex(color.intValue()); // autoboxing
        }
        // set the stipple pattern if specified
        if (patternVar != null) {
            int len = patternVar.getLength();
            if (len != 8 && len != 16 && len != 17) {
                System.out.println("'ART_pattern' length is incorrect");
                return null;
            }

            graphics = graphics.withPatternedOnDisplay(true);
            graphics = graphics.withPatternedOnPrinter(true);
            graphics = graphics.withOutlined(null);
            int[] pattern = new int[16];
            Object obj = patternVar.getObject();
            if (obj instanceof Integer[]) {
                Integer[] pat = (Integer[]) obj;
                if (len == 17) {
                    // the last entry specifies the outline texture
                    int outlineIndex = pat[16].intValue();  // autoboxing
                    graphics = graphics.withOutlined(EGraphics.Outline.findOutline(outlineIndex));
                    len = 16;
                }
                for (int i = 0; i < len; i++) {
                    pattern[i] = pat[i].intValue();  // autoboxing
                }
            } else if (obj instanceof Short[]) {
                Short[] pat = (Short[]) obj;
                for (int i = 0; i < len; i++) {
                    pattern[i] = pat[i].shortValue();
                }
                graphics = graphics.withOutlined(EGraphics.Outline.PAT_S);
            }
            if (len == 8) {
                for (int i = 0; i < 8; i++) {
                    pattern[i + 8] = pattern[i];
                }
            }
            graphics = graphics.withPattern(pattern);
        }
        return graphics;
    }

    /**
     * Method to determ if ArcProto is an Artwork primitive arc
     * @param p ArcProto reference
     * @return true if primitive belongs to the Artwork technology
     */
    public static boolean isArtworkArc(ArcProto p) {
        return (p == Artwork.tech().solidArc || p == Artwork.tech().dottedArc
                || p == Artwork.tech().dashedArc || p == Artwork.tech().thickerArc);
    }
}
