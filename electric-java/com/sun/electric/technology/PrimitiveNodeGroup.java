/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PrimitiveNode.java
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

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.tool.Job;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;

import com.sun.electric.util.math.ECoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A PrimitiveNodeGroup is a group of similar PrimitiveNodes.
 * PrimitiveNodes in a group share a list of NodeLayers. Each PrimitiveNode
 * may omit some NodeLayers of the list.
 */
public class PrimitiveNodeGroup {

    private final Technology tech;
    private final Technology.NodeLayer[] nodeLayers;
    private final List<PrimitiveNode> nodes = new ArrayList<PrimitiveNode>();
    private final List<PrimitiveNode> unmodifiableNodes = Collections.unmodifiableList(nodes);
    private Xml.PrimitiveNodeGroup ng;
    private final EPoint sizeCorrector1;
    private final EPoint sizeCorrector2;
    private final String minSizeRule;
    private final double defaultWidth;
    private final double defaultHeight;
    private final EPoint fullSize;
    private final ERectangle fullRectangle;
    private final ERectangle baseRectangle;
    private final EdgeH[] elx;
    private final EdgeH[] ehx;
    private final EdgeV[] ely;
    private final EdgeV[] ehy;
    private final ArcProto[][] fullConnections;
    
    public List<PrimitiveNode> getNodes() {
        return unmodifiableNodes;
    }
    
    private PrimitiveNodeGroup(Technology tech, Xml.PrimitiveNodeGroup ng, Map<String, Layer> layers, Map<String, ArcProto> arcs) {
        this.tech = tech;
        this.ng = ng;
        EPoint sizeCorrector1 = ng.diskOffset.get(Integer.valueOf(1));
        EPoint sizeCorrector2 = ng.diskOffset.get(Integer.valueOf(2));
        if (sizeCorrector2 == null) {
            sizeCorrector2 = EPoint.ORIGIN;
        }
        if (sizeCorrector1 == null) {
            sizeCorrector1 = sizeCorrector2;
        }
        this.sizeCorrector1 = sizeCorrector1;
        this.sizeCorrector2 = sizeCorrector2;
        long lx, hx, ly, hy;
        if (ng.nodeSizeRule != null) {
            hx = DBMath.lambdaToGrid(0.5 * ng.nodeSizeRule.width);
            lx = -hx;
            hy = DBMath.lambdaToGrid(0.5 * ng.nodeSizeRule.height);
            ly = -hy;
            minSizeRule = ng.nodeSizeRule.rule;
        } else {
            lx = Long.MAX_VALUE;
            hx = Long.MIN_VALUE;
            ly = Long.MAX_VALUE;
            hy = Long.MIN_VALUE;
            for (int i = 0; i < ng.nodeLayers.size(); i++) {
                Xml.NodeLayer nl = ng.nodeLayers.get(i);
                long x, y;
                if (nl.representation == Technology.NodeLayer.BOX || nl.representation == Technology.NodeLayer.MULTICUTBOX) {
                    x = DBMath.lambdaToGrid(nl.lx.value);
                    lx = Math.min(lx, x);
                    hx = Math.max(hx, x);
                    x = DBMath.lambdaToGrid(nl.hx.value);
                    lx = Math.min(lx, x);
                    hx = Math.max(hx, x);
                    y = DBMath.lambdaToGrid(nl.ly.value);
                    ly = Math.min(ly, y);
                    hy = Math.max(hy, y);
                    y = DBMath.lambdaToGrid(nl.hy.value);
                    ly = Math.min(ly, y);
                    hy = Math.max(hy, y);
                } else if (nl.style == Poly.Type.DISC || nl.style == Poly.Type.CIRCLE) {
                    Technology.TechPoint c = nl.techPoints.get(0);
                    Technology.TechPoint r = nl.techPoints.get(1);
                    long cx = c.getX().getAdder().getGrid();
                    long cy = c.getY().getAdder().getGrid();
                    long rx = r.getX().getAdder().getGrid();
                    long ry = r.getY().getAdder().getGrid();
                    long radius;
                    if (cx == rx) {
                        radius = Math.abs(ry - cy);
                    } else if (cy == ry) {
                        radius = Math.abs(rx - cx);
                    } else {
                        throw new UnsupportedOperationException();
                    }
                    lx = Math.min(lx, cx - radius);
                    hx = Math.max(hx, cx + radius);
                    ly = Math.min(ly, cy - radius);
                    hy = Math.max(hy, cy + radius);
                } else if (nl.style == Poly.Type.CIRCLEARC || nl.style == Poly.Type.THICKCIRCLEARC || nl.style.isText()) {
                    throw new UnsupportedOperationException();
                } else {
                    for (Technology.TechPoint p : nl.techPoints) {
                        x = p.getX().getAdder().getGrid();
                        lx = Math.min(lx, x);
                        hx = Math.max(hx, x);
                        y = p.getY().getAdder().getGrid();
                        ly = Math.min(ly, y);
                        hy = Math.max(hy, y);
                    }
                }
            }
            minSizeRule = null;
        }
        fullRectangle = ERectangle.fromGrid(lx, ly, hx - lx, hy - ly);
        fullSize = EPoint.fromGrid((hx - lx + 1) / 2, (hy - ly + 1) / 2);
        nodeLayers = new Technology.NodeLayer[ng.nodeLayers.size()];
        for (int i = 0; i < ng.nodeLayers.size(); i++) {
            Xml.NodeLayer nl = ng.nodeLayers.get(i);
            Layer layer = layers.get(nl.layer);
            Technology.TechPoint[] techPoints;
            if (nl.representation == Technology.NodeLayer.BOX || nl.representation == Technology.NodeLayer.MULTICUTBOX) {
                techPoints = new Technology.TechPoint[2];
                if (!(nl.lx.k <= nl.hx.k && nl.ly.k <= nl.hy.k)) {
                    throw new IllegalArgumentException("In " + layer);
                }
                if (Job.getDebug()) {
                	if (nl.representation != Technology.NodeLayer.MULTICUTBOX && (nl.lx.value > nl.hx.value || nl.ly.value > nl.hy.value)) {
                        System.out.println("Negative-size polygon in primitive node " + tech.getTechName() + ":" + ng.nodes.get(0).name
                                + ", layer " + layer.getName());
                    }
                }
                techPoints[0] = Technology.makeTechPoint(nl.lx, nl.ly);
                techPoints[1] = Technology.makeTechPoint(nl.hx, nl.hy);
            } else {
                techPoints = nl.techPoints.toArray(new Technology.TechPoint[nl.techPoints.size()]);
            }
            Technology.NodeLayer nodeLayer;
            if (nl.representation == Technology.NodeLayer.MULTICUTBOX) {
                nodeLayer = Technology.NodeLayer.makeMulticut(layer, nl.portNum, nl.style, techPoints, nl.sizex, nl.sizey, nl.sep1d, nl.sep2d);
            } else if (ng.specialType == PrimitiveNode.SERPTRANS) {
                nodeLayer = new Technology.NodeLayer(layer, nl.portNum, nl.style, nl.representation, techPoints,
                        ECoord.fromLambdaRoundGrid(nl.lWidth),
                        ECoord.fromLambdaRoundGrid(nl.rWidth),
                        ECoord.fromLambdaRoundGrid(nl.tExtent),
                        ECoord.fromLambdaRoundGrid(nl.bExtent));
            } else {
                nodeLayer = new Technology.NodeLayer(layer, nl.portNum, nl.style, nl.representation, techPoints);
            }
            nodeLayers[i] = nodeLayer;
        }
        lx = DBMath.lambdaToGrid(ng.baseLX.value);
        hx = DBMath.lambdaToGrid(ng.baseHX.value);
        ly = DBMath.lambdaToGrid(ng.baseLY.value);
        hy = DBMath.lambdaToGrid(ng.baseHY.value);
        baseRectangle = ERectangle.fromGrid(lx, ly, hx - lx, hy - ly);
        if (baseRectangle.getWidth() < 0 || baseRectangle.getHeight() < 0) {
        	com.sun.electric.technology.Xml.PrimitiveNode n = ng.nodes.get(0);
            throw new IllegalArgumentException("Node '" + ng.nodes.get(0).name + "' has negative base rectangle");
        }
        defaultWidth = DBMath.round(ng.defaultWidth.value + 2 * fullSize.getLambdaX());
        defaultHeight = DBMath.round(ng.defaultHeight.value + 2 * fullSize.getLambdaY());
        elx = new EdgeH[ng.ports.size()];
        ehx = new EdgeH[ng.ports.size()];
        ely = new EdgeV[ng.ports.size()];
        ehy = new EdgeV[ng.ports.size()];
        fullConnections = new ArcProto[ng.ports.size()][];
        for (int i = 0; i < ng.ports.size(); i++) {
            Xml.PrimitivePort p = ng.ports.get(i);
            if (p.lx.value > p.hx.value || p.lx.k > p.hx.k || p.ly.value > p.hy.value || p.ly.k > p.hy.k) {
                double lX = p.lx.value - fullSize.getLambdaX() * p.lx.k;
                double hX = p.hx.value - fullSize.getLambdaX() * p.hx.k;
                double lY = p.ly.value - fullSize.getLambdaY() * p.ly.k;
                double hY = p.hy.value - fullSize.getLambdaY() * p.hy.k;
                String explain = " (LX=" + TextUtils.formatDouble(p.lx.k / 2) + "W";
                if (lX >= 0) {
                    explain += "+";
                }
                explain += TextUtils.formatDouble(lX) + ", HX=" + TextUtils.formatDouble(p.hx.k / 2) + "W";
                if (hX >= 0) {
                    explain += "+";
                }
                explain += TextUtils.formatDouble(hX) + ", LY=" + TextUtils.formatDouble(p.ly.k / 2) + "H";
                if (lY >= 0) {
                    explain += "+";
                }
                explain += TextUtils.formatDouble(lY) + ", HY=" + TextUtils.formatDouble(p.hy.k / 2) + "H";
                if (hY >= 0) {
                    explain += "+";
                }
                explain += TextUtils.formatDouble(hY);
                explain += " but size is " + fullSize.getLambdaX() * 2 + "x" + fullSize.getLambdaY() * 2 + ")";
                throw new IllegalArgumentException("port " + p.name + " in primitive " + tech.getTechName() + ":" + ng.nodes.get(0).name + " has negative size" + explain);
            }
            elx[i] = Technology.makeEdgeH(p.lx);
            ehx[i] = Technology.makeEdgeH(p.hx);
            ely[i] = Technology.makeEdgeV(p.ly);
            ehy[i] = Technology.makeEdgeV(p.hy);
            fullConnections[i] = Technology.makeConnections(ng.nodes.get(0).name, p.name, p.portArcs, arcs);
        }
    }
    
    private PrimitiveNode makePrimitiveNode(int nodeIndex) {
        Xml.PrimitiveNode n = ng.nodes.get(nodeIndex);
        boolean needElectricalLayers = false;
        assert nodeLayers.length == ng.nodeLayers.size();
        ArrayList<Technology.NodeLayer> visualNodeLayers = new ArrayList<Technology.NodeLayer>();
        ArrayList<Technology.NodeLayer> electricalNodeLayers = new ArrayList<Technology.NodeLayer>();
        for (int i = 0; i < ng.nodeLayers.size(); i++) {
            Xml.NodeLayer nl = ng.nodeLayers.get(i);
            Technology.NodeLayer nodeLayer = nodeLayers[i];
            if (nl.inNodes != null && !nl.inNodes.get(nodeIndex)) {
                continue;
            }
            if (!(nl.inLayers && nl.inElectricalLayers)) {
                needElectricalLayers = true;
            }
            if (nl.inLayers) {
                visualNodeLayers.add(nodeLayer);
            }
            if (nl.inElectricalLayers) {
                electricalNodeLayers.add(nodeLayer);
            }
        }
        
        PrimitiveNode pnp;
        switch (ng.specialType) {
            case com.sun.electric.technology.PrimitiveNode.POLYGONAL:
                pnp = new PrimitiveNode.Polygonal(n.name, tech, sizeCorrector1, sizeCorrector2, minSizeRule,
                        defaultWidth, defaultHeight,
                        fullRectangle, baseRectangle, visualNodeLayers.toArray(new Technology.NodeLayer[visualNodeLayers.size()]));
                break;
            case com.sun.electric.technology.PrimitiveNode.SERPTRANS:
                pnp = new PrimitiveNode.Serpentine(n.name, tech, sizeCorrector1, sizeCorrector2, minSizeRule,
                        defaultWidth, defaultHeight,
                        fullRectangle, baseRectangle,
                        visualNodeLayers.toArray(new Technology.NodeLayer[visualNodeLayers.size()]),
                        ng.specialValues);
                break;
            default:
                pnp = tech.newPrimitiveNode(n.name, sizeCorrector1, sizeCorrector2, minSizeRule,
                        defaultWidth, defaultHeight,
                        fullRectangle, baseRectangle, visualNodeLayers.toArray(new Technology.NodeLayer[visualNodeLayers.size()]));
        }
        if (pnp == null) // error creating the node. Eg. repeated name
        {
            return null;
        }
        
        if (n.oldName != null) {
            tech.oldNodeNames.put(n.oldName, pnp);
        }
        pnp.setFunction(n.function);
        if (needElectricalLayers) {
            pnp.setElectricalLayers(electricalNodeLayers.toArray(new Technology.NodeLayer[electricalNodeLayers.size()]));
        }
        if (ng.shrinkArcs) {
            pnp.setArcsWipe();
            pnp.setArcsShrink();
        }
        if (ng.partialCircle) {
            pnp.setPartialCircle();
        }
        if (ng.square) {
            pnp.setSquare();
        }
        if (ng.canBeZeroSize) {
            pnp.setCanBeZeroSize();
        }
        if (ng.wipes) {
            pnp.setWipeOn1or2();
        }
        if (ng.lockable) {
            pnp.setLockedPrim();
        }
        if (ng.edgeSelect) {
            pnp.setEdgeSelect();
        }
        if (ng.skipSizeInPalette) {
            pnp.setSkipSizeInPalette();
        }
        if (ng.notUsed) {
            pnp.setNotUsed(true);
        }
        if (n.lowVt) {
            pnp.setNodeBit(PrimitiveNode.LOWVTBIT);
        }
        if (n.highVt) {
            pnp.setNodeBit(PrimitiveNode.HIGHVTBIT);
        }
        if (n.nativeBit) {
            pnp.setNodeBit(PrimitiveNode.NATIVEBIT);
        }
        if (n.od18) {
            pnp.setNodeBit(PrimitiveNode.OD18BIT);
        }
        if (n.od25) {
            pnp.setNodeBit(PrimitiveNode.OD25BIT);
        }
        if (n.od33) {
            pnp.setNodeBit(PrimitiveNode.OD33BIT);
        }
        if (n.curvePin) {
            pnp.setNodeBit(PrimitiveNode.CURVEPIN);
        }
        
        PrimitivePort[] ports = new PrimitivePort[ng.ports.size()];
        for (int i = 0; i < ports.length; i++) {
            Xml.PrimitivePort p = ng.ports.get(i);
            PrimitivePort pp;
            switch (ng.specialType) {
                case com.sun.electric.technology.PrimitiveNode.POLYGONAL:
                    pp = new PrimitivePort.Polygonal(pnp, fullConnections[i], p.name, ports.length == 1,
                            p.portAngle, p.portRange, p.portTopology,
                            elx[i], ely[i], ehx[i], ehy[i]);
                    break;
                case com.sun.electric.technology.PrimitiveNode.SERPTRANS:
                    pp = new PrimitivePort.Serpentine(pnp, fullConnections[i], p.name, ports.length == 1,
                            p.portAngle, p.portRange, p.portTopology,
                            elx[i], ely[i], ehx[i], ehy[i]);
                    break;
                default:
                    pp = PrimitivePort.newInstance(pnp, fullConnections[i], p.name, ports.length == 1,
                            p.portAngle, p.portRange, p.portTopology, PortCharacteristic.UNKNOWN, false, false,
                            elx[i], ely[i], ehx[i], ehy[i]);
            }
            ports[i] = pp;
        }
        pnp.addPrimitivePorts(ports);
        if (n.function == PrimitiveNode.Function.NODE) {
            System.out.println("ERROR: Node " + n.name + " must not be a Pure-Layer Node");
            assert n.function != PrimitiveNode.Function.NODE;
        }
        if (ng.spiceTemplate != null) {
            pnp.setSpiceTemplate(ng.spiceTemplate);
        }
        pnp.check();
        return pnp;
    }
    
    static void makePrimitiveNodes(Technology tech, Xml.PrimitiveNodeGroup ng, Map<String, Layer> layers, Map<String, ArcProto> arcs) {
        PrimitiveNodeGroup group = new PrimitiveNodeGroup(tech, ng, layers, arcs);
        for (int i = 0; i < ng.nodes.size(); i++) {
            PrimitiveNode pnp = group.makePrimitiveNode(i);
            if (!ng.isSingleton) {
                group.nodes.add(pnp);
                pnp.group = group;
            }
        }
        if (!ng.isSingleton) {
            tech.primitiveNodeGroups.add(group);
        }
    }
    
    Xml.PrimitiveNodeGroup makeXml() {
        return ng;
    }
}
