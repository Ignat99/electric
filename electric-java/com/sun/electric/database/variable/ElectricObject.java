/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ElectricObject.java
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
package com.sun.electric.database.variable;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableElectricObject;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.text.Name;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.technology.TechPool;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Artwork;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.user.ActivityLogger;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.MutableInteger;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is the base class of all Electric objects that can be extended with "Variables".
 * <P>
 * This class should be thread-safe.
 */
public abstract class ElectricObject implements Serializable {
    // ------------------------ private data ------------------------------------

    // ------------------------ private and protected methods -------------------
    /**
     * The protected constructor.
     */
    protected ElectricObject() {
    }

    /**
     * Returns persistent data of this ElectricObject with Variables.
     * @return persistent data of this ElectricObject.
     */
    public abstract ImmutableElectricObject getD();

    // ------------------------ public methods -------------------
    /**
     * Returns true if object is linked into database
     */
    public abstract boolean isLinked();

    /**
     * Method to return the value of the the Variable on this ElectricObject with a given key and type.
     * @param key the key of the Variable.
     * @param type the required type of the Variable.
     * @return the value Variable with that key and type, or null if there is no such Variable
     * @throws NullPointerException if key is null
     */
    public <T> T getVarValue(Variable.Key key, Class<T> type) {
        return getVarValue(key, type, null);
    }

    /**
     * Method to return the value of the the Variable on this ElectricObject with a given key and type.
     * @param key the key of the Variable.
     * @param type the required type of the Variable.
     * @param defaultValue default value
     * @return the value Variable with that key and type, or defaultValue if there is no such Variable
     * @throws NullPointerException if key or type is null
     */
    public <T> T getVarValue(Variable.Key key, Class<T> type, T defaultValue) {
        Variable var = getVar(key);
        if (var != null) {
            Object value = var.getObject();
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }
        return defaultValue; // null type means any type
    }

    /**
     * Method to return the Variable on this ElectricObject with a given name.
     * @param name the name of the Variable.
     * @return the Variable with that name, or null if there is no such Variable.
     * @throws NullPointerException if name is null
     */
    public Variable getVar(String name) {
        Variable.Key key = Variable.findKey(name);
        return key != null ? getVar(key) : null;
    }

    /**
     * Method to return the Variable on this ElectricObject with a given key.
     * @param key the key of the Variable.
     * @return the Variable with that key, or null if there is no such Variable.
     * @throws NullPointerException if key is null
     */
    public Variable getVar(Variable.Key key) {
        checkExamine();
        return getD().getVar(key);
    }

    /**
     * Method to return the Parameter or Variable on this ElectricObject with a given name.
     * @param name the name of the Parameter or Variable.
     * @return the Parameter or Variable with that key, or null if there is no such Parameter or Variable Variable.
     * @throws NullPointerException if key is null
     */
    public Variable getParameterOrVariable(String name) {
        Variable.Key key = Variable.findKey(name);
        return key != null ? getParameterOrVariable(key) : null;
    }

    /**
     * Method to return the Parameter or Variable on this ElectricObject with a given key.
     * @param key the key of the Parameter or Variable.
     * @return the Parameter or Variable with that key, or null if there is no such Parameter or Variable Variable.
     * @throws NullPointerException if key is null
     */
    public Variable getParameterOrVariable(Variable.Key key) {
        checkExamine();
        return getD().getVar(key);
    }

    /**
     * Returns the TextDescriptor on this ElectricObject selected by variable key.
     * This key may be a key of variable on this ElectricObject or one of the
     * special keys:
     * <code>NodeInst.NODE_NAME</code>
     * <code>NodeInst.NODE_PROTO</code>
     * <code>ArcInst.ARC_NAME</code>
     * <code>Export.EXPORT_NAME</code>
     * The TextDescriptor gives information for displaying the Variable.
     * @param varKey key of variable or special key.
     * @return the TextDescriptor on this ElectricObject.
     */
    public TextDescriptor getTextDescriptor(Variable.Key varKey) {
        Variable var = getParameterOrVariable(varKey);
        if (var == null) {
            return null;
        }
        return var.getTextDescriptor();
    }

    /**
     * Returns the TextDescriptor on this ElectricObject selected by variable key.
     * This key may be a key of variable on this ElectricObject or one of the
     * special keys:
     * <code>NodeInst.NODE_NAME</code>
     * <code>NodeInst.NODE_PROTO</code>
     * <code>ArcInst.ARC_NAME</code>
     * <code>Export.EXPORT_NAME</code>
     * The TextDescriptor gives information for displaying the Variable.
     * @param varKey key of variable or special key.
     * @return the TextDescriptor on this ElectricObject.
     */
    public MutableTextDescriptor getMutableTextDescriptor(Variable.Key varKey) {
        TextDescriptor td = getTextDescriptor(varKey);
        if (td == null) {
            return null;
        }
        return new MutableTextDescriptor(td);
    }

    /**
     * Returns the Code on this ElectricObject selected by variable key.
     * This key may be a key of variable on this ElectricObject or one of the
     * special keys:
     * <code>NodeInst.NODE_NAME</code>
     * <code>NodeInst.NODE_PROTO</code>
     * <code>ArcInst.ARC_NAME</code>
     * <code>Export.EXPORT_NAME</code>
     * The Code gives information for displaying the Variable.
     * @param varKey key of variable or special key.
     * @return the Code on this ElectricObject.
     */
    public CodeExpression.Code getCode(Variable.Key varKey) {
        Variable var = getParameterOrVariable(varKey);
        return var != null ? var.getCode() : CodeExpression.Code.NONE;
    }

    /**
     * Method to return true if the Variable on this ElectricObject with given key is a parameter.
     * Parameters are those Variables that have values on instances which are
     * passed down the hierarchy into the contents.
     * Parameters can only exist on Cell and NodeInst objects.
     * @param varKey key to test
     * @return true if the Variable with given key is a parameter.
     */
    public boolean isParam(Variable.Key varKey) {
        return false;
    }

    /**
     * Method to return a more readable name for Variable on this ElectricObject.
     * The method adds "Parameter" or "Attribute" as appropriate
     * and uses sensible names such as "Diode Size" instead of "SCHEM_diode".
     * @param var Variable on this ElectricObject
     * @return a more readable name for this Variable.
     */
    public String getReadableName(Variable var) {
        String trueName = "";
        String name = var.getKey().getName();
        if (var.isAttribute()) {
            if (isParam(var.getKey())) {
                trueName += "Parameter '" + name.substring(5) + "'";
            } else {
                trueName += "Attribute '" + name.substring(5) + "'";
            }
        } else {
            String betterName = Variable.betterVariableName(name);
            if (betterName != null) {
                trueName += betterName;
            } else {
                trueName += "Variable '" + name + "'";
            }
        }
//		unitname = us_variableunits(var);
//		if (unitname != 0) formatinfstr(infstr, x_(" (%s)"), unitname);
        return trueName;
    }

    /**
     * Method to return a full description of Variable on this ElectricObject.
     * The description includes this object.
     * @param var Variable on this ElectricObject
     * @return a full description of this Variable.
     */
    public String getFullDescription(Variable var) {
        String trueName = getReadableName(var);
        String description = null;
        if (this instanceof Export) {
            description = trueName + " on " + this;
        } else if (this instanceof PortInst) {
            PortInst pi = (PortInst) this;
            description = trueName + " on " + pi.getPortProto()
                    + " of " + pi.getNodeInst().describe(true);
        } else if (this instanceof ArcInst) {
            description = trueName + " on " + this;
        } else if (this instanceof NodeInst) {
            NodeInst ni = (NodeInst) this;
            description = trueName + " on " + ni;
            if (ni.getProto() == Generic.tech().invisiblePinNode) {
                String varName = var.getKey().getName();
                String betterName = Variable.betterVariableName(varName);
                if (betterName != null) {
                    description = betterName;
                }
            }
        } else if (this instanceof Cell) {
            description = trueName + " of " + this;
        }
        return description;
    }

    /**
     * Method to add all displayable Variables on this Electric object to an array of Poly objects.
     * @param rect a rectangle describing the bounds of the object on which the Variables will be displayed.
     * @param polys a list of Poly objects that will be filled with the displayable Variables.
     * @param wnd window in which the Variables will be displayed.
     * @param multipleStrings true to break multi-line text into multiple Polys.
     * @param showTempNames show temporary names on nodes and arcs
     */
    public void addDisplayableVariables(Rectangle2D rect, List<Poly> polys, EditWindow0 wnd, boolean multipleStrings, boolean showTempNames) {
        checkExamine();
        double cX = rect.getCenterX();
        double cY = rect.getCenterY();
        int startOfMyPolys = polys.size();
        for (Iterator<Variable> it = getParametersAndVariables(); it.hasNext();) {
            Variable var = it.next();
            if (!var.isDisplay()) {
                continue;
            }
            addPolyList(polys, var, cX, cY, wnd, multipleStrings);
        }
        for (int i = startOfMyPolys; i < polys.size(); i++) {
            Poly poly = polys.get(i);
            poly.setStyle(Poly.rotateType(poly.getStyle(), this));

        }
    }

    /**
     * Method to get all displayable Variables on this ElectricObject and its PortInsts to an array of Poly objects.
     * @param rect a rectangle describing the bounds of the object on which the Variables will be displayed.
     * @param wnd window in which the Variables will be displayed.
     * @param multipleStrings true to break multi-line text into multiple Polys.
     * @param showTempNames show temporary names on nodes and arcs.
     * @return an array of Poly objects with displayable variables.
     */
    public Poly[] getDisplayableVariables(Rectangle2D rect, EditWindow0 wnd, boolean multipleStrings, boolean showTempNames) {
        List<Poly> polys = new ArrayList<Poly>();
        addDisplayableVariables(rect, polys, wnd, multipleStrings, showTempNames);
        return polys.toArray(Poly.NULL_ARRAY);
    }

    /**
     * Method to compute a Poly that describes text.
     * The text can be described by an ElectricObject (Exports or cell instance names).
     * The text can be described by a node or arc name.
     * The text can be described by a variable on an ElectricObject.
     * @param wnd the EditWindow0 in which the text will be drawn.
     * @param varKey the Variable.Key on the ElectricObject (may be null).
     * @return a Poly that covers the text completely.
     * Even though the Poly is scaled for a particular EditWindow,
     * its coordinates are in object space, not screen space.
     */
    public Poly computeTextPoly(EditWindow0 wnd, Variable.Key varKey) {
        checkExamine();
        Poly poly = null;
        if (varKey != null) {
            LinkedList<Poly> polys = new LinkedList<Poly>();
            if (this instanceof Export) {
                Export pp = (Export) this;
                if (varKey == Export.EXPORT_NAME) {
                    poly = pp.getNamePoly();
                } else {
                    Rectangle2D bounds = pp.getNamePoly().getBounds2D();
                    pp.addPolyList(polys, pp.getVar(varKey), bounds.getCenterX(), bounds.getCenterY(), wnd, false);
                    if (!polys.isEmpty()) {
                        poly = polys.getFirst();
                    }
                }
            } else if (this instanceof PortInst) {
                PortInst pi = (PortInst) this;
                Rectangle2D bounds = pi.getPoly().getBounds2D();
                pi.addPolyList(polys, pi.getVar(varKey), bounds.getCenterX(), bounds.getCenterY(), wnd, false);
                if (!polys.isEmpty()) {
                    poly = polys.getFirst();
                    if (!Poly.NEWTEXTTREATMENT) {
                        poly.transform(pi.getNodeInst().rotateOut());
                    }
                }
            } else if (this instanceof Geometric) {
                Geometric geom = (Geometric) this;
                if (varKey == NodeInst.NODE_PROTO) {
                    if (!(geom instanceof NodeInst)) {
                        return null;
                    }
                    NodeInst ni = (NodeInst) this;
                    TextDescriptor td = ni.getTextDescriptor(NodeInst.NODE_PROTO);
                    Poly.Type style = td.getPos().getPolyType();
                    Poly.Point[] pointList;
                    if (style == Poly.Type.TEXTBOX) {
                        pointList = Poly.makePoints(ni.getBounds());
                    } else {
                        pointList = new Poly.Point[1];
                        pointList[0] = Poly.fromLambda(ni.getTrueCenterX() + td.getXOff(), ni.getTrueCenterY() + td.getYOff());
                    }
                    poly = new Poly(pointList);
                    poly.setStyle(style);
                    poly.setTextDescriptor(td);
                    poly.setString(ni.getProto().describe(false));
                } else {
                    double x = geom.getTrueCenterX();
                    double y = geom.getTrueCenterY();
                    if (geom instanceof NodeInst) {
                        NodeInst ni = (NodeInst) geom;
                        Rectangle2D uBounds = ni.getUntransformedBounds();
                        x = uBounds.getCenterX();
                        y = uBounds.getCenterY();
                    }
//					if (varKey == NodeInst.NODE_NAME || varKey == ArcInst.ARC_NAME)
//					{
                    TextDescriptor td = geom.getTextDescriptor(varKey);
                    Poly.Type style = td.getPos().getPolyType();
                    String theText;
                    int varLength = 1;
                    Variable var = null;
                    if (varKey == NodeInst.NODE_NAME) {
                        theText = ((NodeInst) geom).getName();
                    } else if (varKey == ArcInst.ARC_NAME) {
                        theText = ((ArcInst) geom).getName();
                    } else {
                        VarContext context = null;
                        if (wnd != null) {
                            context = wnd.getVarContext();
                        }
                        var = geom.getParameterOrVariable(varKey);
                        theText = var.describe(0, context, this);
                        varLength = var.getLength();
                    }
                    addPolyListInternal(polys, var, x, y, wnd, false, varLength,
                            style, td, varKey, theText);
                    if (Poly.NEWTEXTTREATMENT && !polys.isEmpty() && varLength == 1) {
                        if (geom instanceof NodeInst) {
                            NodeInst ni = (NodeInst) geom;
                            polys.get(0).transform(ni.rotateOut());
                        }
                    }
//					} else
//					{
//						polys = geom.getPolyList(geom.getParameterOrVariable(varKey), x, y, wnd, false);
//					}
                    if (!polys.isEmpty()) {
                        poly = polys.get(0);
                        if (!Poly.NEWTEXTTREATMENT && geom instanceof NodeInst) {
                            NodeInst ni = (NodeInst) geom;
                            poly.transform(ni.rotateOut());
                        }
                    }
                }
            } else if (this instanceof Cell) {
                Cell cell = (Cell) this;
                cell.addPolyList(polys, cell.getParameterOrVariable(varKey), 0, 0, wnd, false);
                if (!polys.isEmpty()) {
                    poly = polys.getFirst();
                }
            }
        }
        if (poly != null) {
            poly.setExactTextBounds(wnd, this);
        }
        return poly;
    }

    /**
     * Method to return the bounds of this ElectricObject in an EditWindow.
     * @param wnd the EditWindow0 in which the object is being displayed.
     * @return the bounds of the text (does not include the bounds of the object).
     */
    public Rectangle2D getTextBounds(EditWindow0 wnd) {
        Rectangle2D bounds = null;
        for (Iterator<Variable> vIt = getParametersAndVariables(); vIt.hasNext();) {
            Variable var = vIt.next();
            if (!var.isDisplay()) {
                continue;
            }
            Poly poly = computeTextPoly(wnd, var.getKey());
            if (poly == null) {
                continue;
            }
            Rectangle2D polyBound = poly.getBounds2D();
            if (bounds == null) {
                bounds = polyBound;
            } else {
                Rectangle2D.union(bounds, polyBound, bounds);
            }
        }

        if (this instanceof ArcInst) {
            ArcInst ai = (ArcInst) this;
            Name name = ai.getNameKey();
            if (!name.isTempname()) {
                Poly poly = computeTextPoly(wnd, ArcInst.ARC_NAME);
                if (poly != null) {
                    Rectangle2D polyBound = poly.getBounds2D();
                    if (bounds == null) {
                        bounds = polyBound;
                    } else {
                        Rectangle2D.union(bounds, polyBound, bounds);
                    }
                }
            }
        }
        if (this instanceof NodeInst) {
            NodeInst ni = (NodeInst) this;
            Name name = ni.getNameKey();
            if (!name.isTempname()) {
                Poly poly = computeTextPoly(wnd, NodeInst.NODE_NAME);
                if (poly != null) {
                    Rectangle2D polyBound = poly.getBounds2D();
                    if (bounds == null) {
                        bounds = polyBound;
                    } else {
                        Rectangle2D.union(bounds, polyBound, bounds);
                    }
                }
            }
            for (Iterator<Export> it = ni.getExports(); it.hasNext();) {
                Export pp = it.next();
                Poly poly = pp.computeTextPoly(wnd, Export.EXPORT_NAME);
                if (poly != null) {
                    Rectangle2D polyBound = poly.getBounds2D();
                    if (bounds == null) {
                        bounds = polyBound;
                    } else {
                        Rectangle2D.union(bounds, polyBound, bounds);
                    }
                }
            }
        }
        return bounds;
    }

    /**
     * Method to add to a list Poly objects that describes a displayable Variable on this Electric object.
     * @param polys a list of polys to add
     * @param var the Variable on this ElectricObject to describe.
     * @param cX the center X coordinate of the ElectricObject.
     * @param cY the center Y coordinate of the ElectricObject.
     * @param wnd window in which the Variable will be displayed.
     * @param multipleStrings true to break multi-line text into multiple Polys.
     */
    protected void addPolyList(List<Poly> polys, Variable var, double cX, double cY, EditWindow0 wnd, boolean multipleStrings) {
        int varLength = var.getLength();
        Poly.Type style = var.getPos().getPolyType();
        TextDescriptor td = var.getTextDescriptor();

        VarContext context = null;
        if (wnd != null) {
            context = wnd.getVarContext();
        }
        String firstLine = var.describe(0, context, this);
        addPolyListInternal(polys, var, cX, cY, wnd, multipleStrings, varLength, style, td, var.getKey(), firstLine);
    }

    /**
     * Method to add to a list Poly objects that describes text on this Electric object.
     * @param polys a list to add
     * @param var the Variable on this ElectricObject to describe (may be null for node/arc names).
     * @param cX the center X coordinate of the ElectricObject.
     * @param cY the center Y coordinate of the ElectricObject.
     * @param wnd window in which the Variable will be displayed.
     * @param multipleStrings true to break multi-line text into multiple Polys.
     * @param varLength the number of strings in the text.
     * @param style the style of the text.
     * @param td the TextDescriptor of the text.
     * @param varKey the Key of the text.
     * @param firstLine the first line of text (all of it, unless a multi-line Variable).
     */
    private void addPolyListInternal(List<Poly> polys, Variable var, double cX, double cY, EditWindow0 wnd, boolean multipleStrings,
            int varLength, Poly.Type style, TextDescriptor td, Variable.Key varKey, String firstLine) {
        double lineOffX = 0, lineOffY = 0;
        FixpTransform trans = null;
        double offX = td.getXOff();
        double offY = td.getYOff();
        if (this instanceof NodeInst && (offX != 0 || offY != 0)) {
            td = td.withOff(0, 0);
        }
        boolean headerString = false;
        double fontHeight = 1;
        double scale = 1;
        if (wnd != null) {
            fontHeight = td.getTrueSize(wnd);
            scale = wnd.getScale();
            fontHeight *= wnd.getGlobalTextScale();
        }
        if (varLength > 1) {
            // compute text height
            double lineDist = fontHeight / scale;
            int rotQuadrant = td.getRotation().getIndex();
            switch (rotQuadrant) {
                case 0:
                    lineOffY = lineDist;
                    break;		// 0 degrees rotation
                case 1:
                    lineOffX = -lineDist;
                    break;		// 90 degrees rotation
                case 2:
                    lineOffY = -lineDist;
                    break;		// 180 degrees rotation
                case 3:
                    lineOffX = lineDist;
                    break;		// 270 degrees rotation
            }

            // multi-line text on rotated nodes must compensate for node rotation
            Poly.Type rotStyle = style;
            if (this instanceof NodeInst) {
                if (style != Poly.Type.TEXTCENT && style != Poly.Type.TEXTBOX) {
                    NodeInst ni = (NodeInst) this;
                    trans = ni.rotateIn();
                    int origAngle = style.getTextAngle();
                    if (ni.isMirroredAboutXAxis() != ni.isMirroredAboutYAxis()
                            && ((origAngle % 1800) == 0 || (origAngle % 1800) == 1350)) {
                        origAngle += 1800;
                    }
                    int angle = (origAngle - ni.getAngle() + 3600) % 3600;
                    if (!Poly.NEWTEXTTREATMENT) {
                        style = Poly.Type.getTextTypeFromAngle(angle);
                    }
                }
            }
            if (td.getDispPart() == TextDescriptor.DispPos.NAMEVALUE) {
                headerString = true;
                varLength++;
            }
            if (Poly.NEWTEXTTREATMENT && trans != null) {
                if (multipleStrings) {
                    Point2D pt = new Point2D.Double(cX, cY);
                    trans.transform(pt, pt);
                    cX = pt.getX();
                    cY = pt.getY();
                }
            }
            if (multipleStrings) {
                if (rotStyle == Poly.Type.TEXTCENT || rotStyle == Poly.Type.TEXTBOX
                        || rotStyle == Poly.Type.TEXTLEFT || rotStyle == Poly.Type.TEXTRIGHT) {
                    cX += lineOffX * (varLength - 1) / 2;
                    cY += lineOffY * (varLength - 1) / 2;
                }
                if (rotStyle == Poly.Type.TEXTBOT
                        || rotStyle == Poly.Type.TEXTBOTLEFT || rotStyle == Poly.Type.TEXTBOTRIGHT) {
                    cX += lineOffX * (varLength - 1);
                    cY += lineOffY * (varLength - 1);
                }
            } else {
                if (rotStyle == Poly.Type.TEXTCENT || rotStyle == Poly.Type.TEXTBOX
                        || rotStyle == Poly.Type.TEXTLEFT || rotStyle == Poly.Type.TEXTRIGHT) {
                    cX -= lineOffX * (varLength - 1) / 2;
                    cY -= lineOffY * (varLength - 1) / 2;
                }
                if (rotStyle == Poly.Type.TEXTTOP
                        || rotStyle == Poly.Type.TEXTTOPLEFT || rotStyle == Poly.Type.TEXTTOPRIGHT) {
                    cX -= lineOffX * (varLength - 1);
                    cY -= lineOffY * (varLength - 1);
                }
                varLength = 1;
                headerString = false;
            }
        }

        VarContext context = null;
        if (wnd != null) {
            context = wnd.getVarContext();
        }
        for (int i = 0; i < varLength; i++) {
            String message = null;
            TextDescriptor entryTD = td;
            if (varLength > 1 && headerString) {
                if (i == 0) {
                    message = var.getTrueName() + "[" + (varLength - 1) + "]:";
                    entryTD = entryTD.withUnderline(true);
                } else {
                    message = i == 1 ? firstLine : var.describe(i - 1, context, this);
                }
            } else {
                message = i == 0 ? firstLine : var.describe(i, context, this);
            }

            Poly.Point[] pointList;
            if (style == Poly.Type.TEXTBOX && this instanceof Geometric) {
                Geometric geom = (Geometric) this;
                Rectangle2D bounds = geom.getBounds();
                pointList = Poly.makePoints(bounds);
            } else {
                pointList = new Poly.Point[1];
                pointList[0] = Poly.fromLambda(cX + offX, cY + offY);
                if (trans != null) {
                    if (!Poly.NEWTEXTTREATMENT || multipleStrings) {
                        trans.transform(pointList[0], pointList[0]);
                    }
                }
            }
            Poly poly = new Poly(pointList);
            poly.setString(message);
            poly.setStyle(style);
            poly.setTextDescriptor(entryTD);
            poly.setDisplayedText(new DisplayedText(this, varKey));
            poly.setLayer(null);
            polys.add(poly);
            cX -= lineOffX;
            cY -= lineOffY;
        }
    }

    /**
     * Method to create a non-displayable Variable on this ElectricObject with the specified values.
     * @param name the name of the Variable.
     * @param value the object to store in the Variable.
     * @return the Variable that has been created.
     * @deprecated Use method with explicit EditingPreferences parameter.
     */
    public Variable newVar(String name, Object value) {
        return newVar(name, value, EditingPreferences.getInstance());
    }

    /**
     * Method to create a non-displayable Variable on this ElectricObject with the specified values.
     * @param name the name of the Variable.
     * @param value the object to store in the Variable.
     * @param ep EditingPreferences with default TextDescriptors
     * @return the Variable that has been created.
     */
    public Variable newVar(String name, Object value, EditingPreferences ep) {
        return newVar(Variable.newKey(name), value, ep);
    }

    /**
     * Method to create a displayable Variable on this ElectricObject with the specified values.
     * @param key the key of the Variable.
     * @param value the object to store in the Variable.
     * @return the Variable that has been created.
     * @deprecated Use method with explicit EditingPreferences parameter.
     */
    public Variable newDisplayVar(Variable.Key key, Object value) {
        return newDisplayVar(key, value, EditingPreferences.getInstance());
    }

    /**
     * Method to create a displayable Variable on this ElectricObject with the specified values.
     * @param key the key of the Variable.
     * @param value the object to store in the Variable.
     * @param ep EditingPreferences with default TextDescriptors
     * @return the Variable that has been created.
     */
    public Variable newDisplayVar(Variable.Key key, Object value, EditingPreferences ep) {
        return newVar(key, value, ep, true);
    }

    /**
     * Method to create a non-displayable Variable on this ElectricObject with the specified values.
     * Notify to observers as well.
     * @param key the key of the Variable.
     * @param value the object to store in the Variable.
     * @return the Variable that has been created.
     * @deprecated Use method with explicit EditingPreferences parameter.
     */
    public Variable newVar(Variable.Key key, Object value) {
        return newVar(key, value, EditingPreferences.getInstance());
    }

    /**
     * Method to create a non-displayable Variable on this ElectricObject with the specified values.
     * Notify to observers as well.
     * @param key the key of the Variable.
     * @param value the object to store in the Variable.
     * @param ep EditingPreferences with default TextDescriptors
     * @return the Variable that has been created.
     */
    public Variable newVar(Variable.Key key, Object value, EditingPreferences ep) {
        return newVar(key, value, ep, false);
    }

    /**
     * Method to create a Variable on this ElectricObject with the specified values.
     * @param key the key of the Variable.
     * @param value the object to store in the Variable.
     * @param ep EditingPreferences with default TextDescriptors
     * @param display true if the Variable is displayable.
     * @return the Variable that has been created.
     */
    private Variable newVar(Variable.Key key, Object value, EditingPreferences ep, boolean display) {
        TextDescriptor.TextType textType;

        if (this instanceof Cell) {
            textType = TextDescriptor.TextType.CELL;
        } else if (this instanceof Export) {
            textType = TextDescriptor.TextType.EXPORT;
        } else if (this instanceof NodeInst) {
            textType = TextDescriptor.TextType.NODE;
        } else if (this instanceof ArcInst) {
            textType = TextDescriptor.TextType.ARC;
        } else {
            textType = TextDescriptor.TextType.ANNOTATION;
        }
        TextDescriptor td = ep.getTextDescriptor(textType, display);
        return newVar(key, value, td);
    }

    /**
     * Method to create a Variable on this ElectricObject with the specified values.
     * @param key the key of the Variable.
     * @param value the object to store in the Variable.
     * @param td text descriptor of the Variable
     * @return the Variable that has been created.
     */
    public Variable newVar(Variable.Key key, Object value, TextDescriptor td) {
        if (value == null) {
            return null;
        }
        if (isDeprecatedVariable(key)) {
            System.out.println("Deprecated variable " + key + " on " + this);
        }
        Variable var = null;
        try {
            var = Variable.newInstance(key, value, td);
        } catch (IllegalArgumentException e) {
            ActivityLogger.logException(e);
            return null;
        }
        addVar(var);
        return getVar(key);
//        setChanged();
//        notifyObservers(v);
//        clearChanged();
    }

    /**
     * Method to add a Variable on this ElectricObject.
     * It may add a repaired copy of this Variable in some cases.
     * @param var Variable to add.
     */
    public abstract void addVar(Variable var);

    /**
     * Method to update a Variable on this ElectricObject with the specified values.
     * If the Variable already exists, only the value is changed; the displayable attributes are preserved.
     * @param key the key of the Variable.
     * @param value the object to store in the Variable.
     * @param ep EditingPreferences with default TextDescriptors
     * @return the Variable that has been updated.
     */
    public Variable updateVar(Variable.Key key, Object value, EditingPreferences ep) {
        Variable var = getVar(key);
        if (var == null) {
            return newVar(key, value, ep);
        }
        addVar(var.withObject(value));
        return getVar(key);
    }

    /**
     * Method to update a text Variable on this ElectricObject with the specified values.
     * If the Variable already exists, only the value is changed;
     * the displayable attributes and Code are preserved.
     * @param key the key of the Variable.
     * @param text the text to store in the Variable.
     * @param ep EditingPreferences with default TextDescriptors
     * @return the Variable that has been updated.
     */
    public Variable updateVarText(Variable.Key key, String text, EditingPreferences ep) {
        Variable var = getVar(key);
        if (var == null) {
            return newVar(key, text, ep);
        }
        addVar(var.withText(text));
        return getVar(key);
    }

    /**
     * Method to update a Variable on this ElectricObject with the specified code.
     * If the Variable already exists, only the code is changed;
     * the displayable attributes and value are preserved.
     * @param key the key of the Variable.
     * @param code the new code of the Variable.
     * @return the Variable that has been updated.
     */
    public Variable updateVarCode(Variable.Key key, CodeExpression.Code code) {
        Variable var = getVar(key);
        if (var == null) {
            return null;
        }
        addVar(var.withCode(code));
        return getVar(key);
    }

    /**
     * Updates the TextDescriptor on this ElectricObject selected by varKey.
     * The varKey may be a key of variable on this ElectricObject or one of the
     * special keys:
     * NodeInst.NODE_NAME
     * NodeInst.NODE_PROTO
     * ArcInst.ARC_NAME
     * Export.EXPORT_NAME
     * If varKey doesn't select any text descriptor, no action is performed.
     * The TextDescriptor gives information for displaying the Variable.
     * @param varKey key of variable or special key.
     * @param td new value TextDescriptor
     */
    public void setTextDescriptor(Variable.Key varKey, TextDescriptor td) {
        Variable var = getVar(varKey);
        if (var == null) {
            return;
        }
        td = td.withParam(false);
        addVar(var.withTextDescriptor(td));
    }

    /**
     * Method to set the X and Y offsets of the text in the TextDescriptor selected by key of
     * variable or special key.
     * The values are scaled by 4, so a value of 3 indicates a shift of 0.75 and a value of 4 shifts by 1.
     * @param varKey key of variable or special key.
     * @param xd the X offset of the text in the TextDescriptor.
     * @param yd the Y offset of the text in the TextDescriptor.
     * @see #setTextDescriptor(com.sun.electric.database.variable.Variable.Key,com.sun.electric.database.variable.TextDescriptor)
     * @see com.sun.electric.database.variable.Variable#withOff(double,double)
     */
    public synchronized void setOff(Variable.Key varKey, double xd, double yd) {
        TextDescriptor td = getTextDescriptor(varKey);
        if (td != null) {
            setTextDescriptor(varKey, td.withOff(xd, yd));
        }
    }

    /**
     * Method to copy text descriptor from another ElectricObject to this ElectricObject.
     * @param other the other ElectricObject from which to copy Variables.
     * @param varKey selector of textdescriptor
     */
    public void copyTextDescriptorFrom(ElectricObject other, Variable.Key varKey) {
        TextDescriptor td = other.getTextDescriptor(varKey);
        if (td == null) {
            return;
        }
        setTextDescriptor(varKey, td);
    }

    /**
     * Rename a Variable. Note that this creates a new variable of
     * the new name and copies all values from the old variable, and
     * then deletes the old variable.
     * @param name the name of the var to rename
     * @param newName the new name of the variable
     * @return the new renamed variable
     */
    public Variable renameVar(String name, String newName) {
        return renameVar(Variable.findKey(name), newName);
    }

    /**
     * Rename a Variable. Note that this creates a new variable of
     * the new name and copies all values from the old variable, and
     * then deletes the old variable.
     * @param key the name key of the var to rename
     * @param newName the new name of the variable
     * @return the new renamed variable, or null on error (no action taken)
     */
    public Variable renameVar(Variable.Key key, String newName) {
        // see if newName exists already
        Variable.Key newKey = Variable.newKey(newName);
        Variable var = getVar(newKey);
        if (var != null) {
            return null;            // name already exists
        }
        // get current Variable
        Variable oldvar = getVar(key);
        if (oldvar == null) {
            return null;
        }

        // create new var
        Variable newVar = newVar(newKey, oldvar.getObject(), oldvar.getTextDescriptor());
        if (newVar == null) {
            return null;
        }
        // copy settings from old var to new var
//        newVar.setTextDescriptor();
//        newVar.copyFlags(oldvar);
        // delete old var
        delVar(oldvar.getKey());

        return newVar;
    }

    /**
     * Method to delete a Variable from this ElectricObject.
     * @param key the key of the Variable to delete.
     */
    public abstract void delVar(Variable.Key key);

    private static class ArrayName {

        private String baseName;
        private String indexPart;
    }

    /**
     * Method to return a unique object name in a Cell.
     * @param name the original name that is not unique.
     * @param cell the Cell in which this name resides.
     * @param cls the class of the object on which this name resides.
     * @param leaveIndexValues true to leave the index values untouched
     * (i.e. "m[17]" will become "m_1[17]" instead of "m[18]").
     * @param fromRight true to increment multidimensional arrays starting at the rightmost index.
     * @return a unique name for that class in that Cell.
     */
    public static String uniqueObjectName(String name, Cell cell, Class<?> cls, boolean leaveIndexValues, boolean fromRight) {
        String newName = name;
        for (int i = 0; !cell.isUniqueName(newName, cls, null); i++) {
            newName = uniqueObjectNameLow(newName, cell, cls, null, null, leaveIndexValues, fromRight);
            if (i > 100) {
                System.out.println("Can't create unique object name in " + cell + " from original " + name + " attempted " + newName);
                return null;
            }
        }
        return newName;
    }

    /**
     * Method to return a unique object name in a Cell.
     * @param name the original name that is not unique.
     * @param cell the Cell in which this name resides.
     * @param cls the class of the object on which this name resides.
     * @param already a Set of names already in use.
     * @param leaveIndexValues true to leave the index values untouched
     * (i.e. "m[17]" will become "m_1[17]" instead of "m[18]").
     * @param fromRight true to increment multidimensional arrays starting at the rightmost index.
     * @return a unique name for that class in that Cell.
     */
    public static String uniqueObjectName(String name, Cell cell, Class<?> cls, Set<String> already,
            Map<String, MutableInteger> nextPlainIndex, boolean leaveIndexValues, boolean fromRight) {
        String newName = name;
        String lcName = newName/*TextUtils.canonicString(newName)*/;
        for (int i = 0; already.contains(lcName); i++) {
            newName = uniqueObjectNameLow(newName, cell, cls, already, nextPlainIndex, leaveIndexValues, fromRight);
            if (i > 100) {
                System.out.println("Can't create unique object name in " + cell + " from original " + name + " attempted " + newName);
                return null;
            }
            lcName = newName/*TextUtils.canonicString(newName)*/;
        }
        return newName;
    }

    /**
     * Internal method to return a unique object name in a Cell.
     * @param name the original name that is not unique.
     * @param cell the Cell in which this name resides.
     * @param cls the class of the object on which this name resides.
     * @param already a Set of names already in use.
     * @param leaveIndexValues true to leave the index values untouched
     * (i.e. "m[17]" will become "m_1[17]" instead of "m[18]").
     * @param fromRight true to increment multidimensional arrays starting at the rightmost index.
     * @return a unique name for that class in that Cell.
     */
    private static String uniqueObjectNameLow(String name, Cell cell, Class<?> cls, Set<String> already,
            Map<String, MutableInteger> nextPlainIndex, boolean leaveIndexValues, boolean fromRight) {
        // first see if the name is unique
        if (already != null) {
            if (!already.contains(name)) {
                return name;
            }
        } else {
            if (cell.isUniqueName(name, cls, null)) {
                return name;
            }
        }

        // see if there is a "++" anywhere to tell us what to increment
        int plusPlusPos = name.indexOf("++");
        if (plusPlusPos >= 0) {
            int numStart = plusPlusPos;
            while (numStart > 0 && TextUtils.isDigit(name.charAt(numStart - 1))) {
                numStart--;
            }
            if (numStart < plusPlusPos) {
                int nextIndex = TextUtils.atoi(name.substring(numStart)) + 1;
                for (;; nextIndex++) {
                    String newname = name.substring(0, numStart) + nextIndex + name.substring(plusPlusPos);
                    if (already != null) {
                        if (!already.contains(newname)) {
                            return newname;
                        }
                    } else {
                        if (cell.isUniqueName(newname, cls, null)) {
                            return newname;
                        }
                    }
                }
            }
        }

        // see if there is a "--" anywhere to tell us what to decrement
        int minusMinusPos = name.indexOf("--");
        if (minusMinusPos >= 0) {
            int numStart = minusMinusPos;
            while (numStart > 0 && TextUtils.isDigit(name.charAt(numStart - 1))) {
                numStart--;
            }
            if (numStart < minusMinusPos) {
                int nextIndex = TextUtils.atoi(name.substring(numStart)) - 1;
                for (; nextIndex >= 0; nextIndex--) {
                    String newname = name.substring(0, numStart) + nextIndex + name.substring(minusMinusPos);
                    if (already != null) {
                        if (!already.contains(newname)) {
                            return newname;
                        }
                    } else {
                        if (cell.isUniqueName(newname, cls, null)) {
                            return newname;
                        }
                    }
                }
            }
        }

        // break the string into a list of ArrayName objects
        List<ArrayName> names = new ArrayList<ArrayName>();
        boolean inBracket = false;
        int len = name.length();
        int startOfBase = 0;
        int startOfIndex = -1;
        for (int i = 0; i < len; i++) {
            char ch = name.charAt(i);
            if (ch == '[') {
                if (startOfIndex < 0) {
                    startOfIndex = i;
                }
                inBracket = true;
            }
            if (ch == ']') {
                inBracket = false;
            }
            if ((ch == ',' && !inBracket) || i == len - 1) {
                // remember this array name
                if (i == len - 1) {
                    i++;
                }
                ArrayName an = new ArrayName();
                int endOfBase = startOfIndex;
                if (endOfBase < 0) {
                    endOfBase = i;
                }
                an.baseName = name.substring(startOfBase, endOfBase);
                if (startOfIndex >= 0) {
                    an.indexPart = name.substring(startOfIndex, i);
                }
                names.add(an);
                startOfBase = i + 1;
                startOfIndex = -1;
            }
        }

        char separateChar = '_';
        for (ArrayName an : names) {
            // adjust the index part if possible
            boolean indexAdjusted = false;
            String index = an.indexPart;
            if (index != null && !leaveIndexValues) {
                // make a list of bracketed expressions
                List<BracketedExpression> bracketedExpressions = new ArrayList<BracketedExpression>();
                int pos = 0;
                for (;;) {
                    int st = index.indexOf('[', pos);
                    if (st < 0) {
                        break;
                    }
                    int en = index.indexOf(']', st);
                    if (en < 0) {
                        break;
                    }
                    BracketedExpression be = new BracketedExpression();
                    be.start = st;
                    be.end = en;
                    if (fromRight) {
                        bracketedExpressions.add(0, be);
                    } else {
                        bracketedExpressions.add(be);
                    }
                    pos = en;
                }

                // now scan the expressions to find one that can be incremented
                int possibleEnd = 0;
                int possibleStart = -1;
                for (BracketedExpression be : bracketedExpressions) {
                    // find the range of characters in square brackets
                    int startPos = be.start;
                    int endPos = be.end;
                    String expr = index.substring(startPos + 1, endPos);

                    // if there is a comma in the bracketed expression, it cannot be incremented
                    if (expr.indexOf(',') >= 0) {
                        continue;
                    }

                    // see if there is a colon in the bracketed expression
                    int i = expr.indexOf(':');
                    if (i >= 0) {
                        // colon: make sure there are two numbers
                        String firstIndex = expr.substring(0, i);
                        String secondIndex = expr.substring(i + 1);
                        if (TextUtils.isANumber(firstIndex) && TextUtils.isANumber(secondIndex)) {
                            int startIndex = TextUtils.atoi(firstIndex);
                            int endIndex = TextUtils.atoi(secondIndex);
                            int spacing = Math.abs(endIndex - startIndex) + 1;
                            for (int nextIndex = 1;; nextIndex++) {
                                String newIndex = index.substring(0, startPos) + "[" + (startIndex + spacing * nextIndex)
                                        + ":" + (endIndex + spacing * nextIndex) + index.substring(endPos);
                                boolean unique;
                                String indexedName = an.baseName + newIndex;
                                if (already != null) {
                                    unique = !already.contains(indexedName);
                                } else {
                                    unique = cell.isUniqueName(indexedName, cls, null);
                                }
                                if (unique) {
                                    indexAdjusted = true;
                                    an.indexPart = newIndex;
                                    break;
                                }
                            }
                            if (indexAdjusted) {
                                break;
                            }
                        }

                        // this bracketed expression cannot be incremented: move on
                        continue;
                    }

                    // see if this bracketed expression is a pure number
                    if (TextUtils.isANumber(expr)) {
                        int nextIndex = TextUtils.atoi(expr) + 1;
                        for (;; nextIndex++) {
                            String newIndex = index.substring(0, startPos) + "[" + nextIndex + index.substring(endPos);
                            boolean unique;
                            String indexedName = an.baseName + newIndex;
                            if (already != null) {
                                unique = !already.contains(indexedName);
                            } else {
                                unique = cell.isUniqueName(indexedName, cls, null);
                            }
                            if (unique) {
                                indexAdjusted = true;
                                an.indexPart = newIndex;
                                break;
                            }
                        }
                        if (indexAdjusted) {
                            break;
                        }
                    }

                    // remember the first index that could be incremented in a pinch
                    if (possibleStart < 0) {
                        possibleStart = startPos;
                        possibleEnd = endPos;
                    }
                }

//            	// see if the index part can be incremented
//            	int possibleEnd = 0;
//                int nameLen = index.length();
//                int possibleStart = -1;
//                int endPos = nameLen - 1;
//                for (;;)
//                {
//                    // find the range of characters in square brackets
//                    int startPos = index.lastIndexOf('[', endPos);
//                    if (startPos < 0) {
//                        break;
//                    }
//
//                    // see if there is a comma in the bracketed expression
//                    int i = index.indexOf(',', startPos);
//                    if (i >= 0 && i < endPos) {
//                        // this bracketed expression cannot be incremented: move on
//                        if (startPos > 0 && index.charAt(startPos - 1) == ']') {
//                            endPos = startPos - 1;
//                            continue;
//                        }
//                        break;
//                    }
//
//                    // see if there is a colon in the bracketed expression
//                    i = index.indexOf(':', startPos);
//                    if (i >= 0 && i < endPos) {
//                        // colon: make sure there are two numbers
//                        String firstIndex = index.substring(startPos + 1, i);
//                        String secondIndex = index.substring(i + 1, endPos);
//                        if (TextUtils.isANumber(firstIndex) && TextUtils.isANumber(secondIndex)) {
//                            int startIndex = TextUtils.atoi(firstIndex);
//                            int endIndex = TextUtils.atoi(secondIndex);
//                            int spacing = Math.abs(endIndex - startIndex) + 1;
//                            for (int nextIndex = 1;; nextIndex++) {
//                                String newIndex = index.substring(0, startPos) + "[" + (startIndex + spacing * nextIndex)
//                                        + ":" + (endIndex + spacing * nextIndex) + index.substring(endPos);
//                                boolean unique;
//                                if (already != null) {
//                                    unique = !already.contains(TextUtils.canonicString(an.baseName + newIndex));
//                                } else {
//                                    unique = cell.isUniqueName(an.baseName + newIndex, cls, null);
//                                }
//                                if (unique) {
//                                    indexAdjusted = true;
//                                    an.indexPart = newIndex;
//                                    break;
//                                }
//                            }
//                            if (indexAdjusted) {
//                                break;
//                            }
//                        }
//
//                        // this bracketed expression cannot be incremented: move on
//                        if (startPos > 0 && index.charAt(startPos - 1) == ']') {
//                            endPos = startPos - 1;
//                            continue;
//                        }
//                        break;
//                    }
//
//                    // see if this bracketed expression is a pure number
//                    String bracketedExpression = index.substring(startPos + 1, endPos);
//                    if (TextUtils.isANumber(bracketedExpression)) {
//                        int nextIndex = TextUtils.atoi(bracketedExpression) + 1;
//                        for (;; nextIndex++) {
//                            String newIndex = index.substring(0, startPos) + "[" + nextIndex + index.substring(endPos);
//                            boolean unique;
//                            if (already != null) {
//                                unique = !already.contains(TextUtils.canonicString(an.baseName + newIndex));
//                            } else {
//                                unique = cell.isUniqueName(an.baseName + newIndex, cls, null);
//                            }
//                            if (unique) {
//                                indexAdjusted = true;
//                                an.indexPart = newIndex;
//                                break;
//                            }
//                        }
//                        if (indexAdjusted) {
//                            break;
//                        }
//                    }
//
//                    // remember the first index that could be incremented in a pinch
//                    if (possibleStart < 0) {
//                        possibleStart = startPos;
//                        possibleEnd = endPos;
//                    }
//
//                    // this bracketed expression cannot be incremented: move on
//                    if (startPos > 0 && index.charAt(startPos - 1) == ']') {
//                        endPos = startPos - 1;
//                        continue;
//                    }
//                    break;
//                }

                // if there was a possible place to increment, do it
                if (!indexAdjusted && possibleStart >= 0) {
                    // nothing simple, but this one can be incremented
                    int i;
                    for (i = possibleEnd - 1; i > possibleStart; i--) {
                        if (!TextUtils.isDigit(index.charAt(i))) {
                            break;
                        }
                    }
                    int nextIndex = TextUtils.atoi(index.substring(i + 1)) + 1;
                    int startPos = i + 1;
                    if (index.charAt(startPos - 1) == separateChar) {
                        startPos--;
                    }
                    for (;; nextIndex++) {
                        String newIndex = index.substring(0, startPos) + separateChar + nextIndex + index.substring(possibleEnd);
                        boolean unique;
                        String indexedName = an.baseName + newIndex;
                        if (already != null) {
                            unique = !already.contains(indexedName);
                        } else {
                            unique = cell.isUniqueName(indexedName, cls, null);
                        }
                        if (unique) {
                            indexAdjusted = true;
                            an.indexPart = newIndex;
                            break;
                        }
                    }
                }
            }

            // if the index was not adjusted, adjust the base part
            if (!indexAdjusted) {
                // array contents cannot be incremented: increment base name
                String base = an.baseName;
                int startPos = base.length();
                int endPos = base.length();

                // if there is a numeric part at the end, increment that
                String localSepString = String.valueOf(separateChar);
                while (startPos > 0 && TextUtils.isDigit(base.charAt(startPos - 1))) {
                    startPos--;
                }
                int nextIndex = 1;
                if (startPos >= endPos) {
                    if (startPos > 0 && base.charAt(startPos - 1) == separateChar) {
                        startPos--;
                    }
                } else {
                    nextIndex = TextUtils.atoi(base.substring(startPos)) + 1;
                    localSepString = "";
                }

                // find the unique index to use
                String prefix = base.substring(0, startPos) + localSepString;
                String suffix = base.substring(endPos);
                if (an.indexPart != null) {
                    suffix += an.indexPart;
                }
                if (nextPlainIndex != null) {
                    String prefixAndSuffix = prefix + '0' + suffix;
                    MutableInteger nxt = nextPlainIndex.get(prefixAndSuffix);
                    if (nxt == null) {
                        nxt = new MutableInteger(cell.getUniqueNameIndex(prefix, suffix, cls, nextIndex));
                        nextPlainIndex.put(prefixAndSuffix, nxt);
                    }
                    nextIndex = nxt.intValue();
                    nxt.increment();
                } else {
                    nextIndex = cell.getUniqueNameIndex(prefix, suffix, cls, nextIndex);
                }
                an.baseName = prefix + nextIndex + base.substring(endPos);
            }
        }
        StringBuffer result = new StringBuffer();
        boolean first = true;
        for (ArrayName an : names) {
            if (first) {
                first = false;
            } else {
                result.append(",");
            }
            result.append(an.baseName);
            if (an.indexPart != null) {
                result.append(an.indexPart);
            }
        }
        return result.toString();
    }

    /**
     * Class to describe bracketed expressions in a name.
     */
    private static class BracketedExpression {

        int start, end;
    }

    /**
     * Method to determine whether a Variable key on this object is deprecated.
     * Deprecated Variable keys are those that were used in old versions of Electric,
     * but are no longer valid.
     * @param key the key of the Variable.
     * @return true if the Variable key is deprecated.
     */
    public boolean isDeprecatedVariable(Variable.Key key) {
        String name = key.toString();
        if (name.length() == 0) {
            return true;
        }
        if (name.length() == 1) {
            char chr = name.charAt(0);
            if (!Character.isLetter(chr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method to return an Iterator over all Variables on this ElectricObject.
     * @return an Iterator over all Variables on this ElectricObject.
     */
    public synchronized Iterator<Variable> getVariables() {
        return getD().getVariables();
    }

    /**
     * Method to return the number of Variables on this ElectricObject.
     * @return the number of Variables on this ElectricObject.
     */
    public synchronized int getNumVariables() {
        return getD().getNumVariables();
    }

    /**
     * Method to return an Iterator over all Parameters and Variables on this ElectricObject.
     * @return an Iterator over all Parameters and Variables on this ElectricObject.
     */
    public Iterator<Variable> getParametersAndVariables() {
        return getVariables();
    }

    /**
     * Routing to check whether changing of this cell allowed or not.
     * By default checks whole database change. Overridden in subclasses.
     */
    public void checkChanging() {
        EDatabase database = getDatabase();
        if (database != null) {
            database.checkChanging();
        }
    }

    /**
     * Routing to check whether undoing of this cell allowed or not.
     * By default checks whole database undo. Overridden in subclasses.
     */
    public void checkUndoing() {
        getDatabase().checkUndoing();
    }

    /**
     * Method to make sure that this object can be examined.
     * Ensures that an examine job is running.
     */
    public void checkExamine() {
        EDatabase database = getDatabase();
        if (database != null) {
            database.checkExamine();
        }
    }

    /**
     * Returns database to which this ElectricObject belongs.
     * Some objects are not in database, for example Geometrics in PaletteFrame.
     * Method returns null for non-database objects.
     * @return database to which this ElectricObject belongs.
     */
    public abstract EDatabase getDatabase();

    /** Returns TechPool of this database */
    public TechPool getTechPool() {
        return getDatabase().getTechPool();
    }

//    /**
//     * Get Technology by TechId
//     * TechId must belong to same IdManager as TechPool
//     * @param techId TechId to find
//     * @return Technology b given TechId or null
//     * @throws IllegalArgumentException of TechId is not from this IdManager
//     */
//    public Technology getTech(TechId techId) {
//        return getTechPool().getTech(techId);
//    }

    /** Returns Artwork technology in this database */
    public Artwork getArtwork() {
        return getTechPool().getArtwork();
    }

    /** Returns Generic technology in this database */
    public Generic getGeneric() {
        return getTechPool().getGeneric();
    }

    /** Returns Schematics technology in this database */
    public Schematics getSchematics() {
        return getTechPool().getSchematics();
    }

    /**
     * Method which indicates that this object is in database.
     * Some objects are not in database, for example Geometrics in PaletteFrame.
     * @return true if this object is in database, false if it is not a database object,
     * or if it is a dummy database object (considered not to be in the database).
     */
    protected boolean isDatabaseObject() {
        return true;
    }

    /**
     * Method to determine the appropriate Cell associated with this ElectricObject.
     * @return the appropriate Cell associated with this ElectricObject.
     * Returns null if no Cell can be found.
     */
    public Cell whichCell() {
        return null;
    }

    /**
     * Method to write a description of this ElectricObject (lists all Variables).
     * Displays the description in the Messages Window.
     */
    public void getInfo() {
        checkExamine();
        boolean firstvar = true;
        for (Iterator<Variable> it = getParametersAndVariables(); it.hasNext();) {
            Variable val = it.next();
            Variable.Key key = val.getKey();
            if (val == null) {
                continue;
            }
            if (firstvar) {
                System.out.println("Variables:");
            }
            firstvar = false;
            Object addr = val.getObject();
            String par = isParam(key) ? "(param)" : "";
//			String par = val.isParam() ? "(param)" : "";
            if (addr instanceof Object[]) {
                Object[] ary = (Object[]) addr;
                System.out.print("   " + key.getName() + "(" + ary.length + ") = [");
                for (int i = 0; i < ary.length; i++) {
                    if (i > 4) {
                        System.out.print("...");
                        break;
                    }
                    if (ary[i] instanceof String) {
                        System.out.print("\"");
                    }
                    System.out.print(ary[i]);
                    if (ary[i] instanceof String) {
                        System.out.print("\"");
                    }
                    if (i < ary.length - 1) {
                        System.out.print(", ");
                    }
                }
                System.out.println("] " + par);
            } else {
                System.out.println("   " + key.getName() + "= " + addr + " " + par);
            }
        }
    }

    /**
     * Returns a printable version of this ElectricObject.
     * @return a printable version of this ElectricObject.
     */
    public String toString() {
        return getClass().getName();
    }

//    /**
//     * Observer method to update variables in Icon instance if cell master changes
//     * @param o
//     * @param arg
//     */
//    public void update(Observable o, Object arg)
//    {
//        System.out.println("Entering update");
//        // New
//        if (arg instanceof Variable)
//        {
//            Variable var = (Variable)arg;
//            // You can't call newVar(var.getKey(), var.getObject()) to avoid infinite loop
//            newVar(var.getD());
//        }
//        else if (arg instanceof Object[])
//        {
//            Object[] array = (Object[])arg;
//
//            if (!(array[0] instanceof String))
//            {
//                System.out.println("Error in ElectricObject.update");
//                return;
//            }
//            String function = (String)array[0];
//            if (function.startsWith("setTextDescriptor"))
//            {
//                Variable.Key varKey = (Variable.Key)array[1];
//                TextDescriptor td = (TextDescriptor)array[2];
//                // setTextDescriptor(String varName, TextDescriptor td)
//                setTextDescriptor(varKey, td);
//            }
//            else if (function.startsWith("delVar"))
//            {
//                Variable.Key key = (Variable.Key)array[1];
//                delVarNoObserver(key);
//            }
////            else if (array[0] instanceof Variable.Key)
////            {
////                //  Variable updateVar(String name, Object value)
////                Variable.Key key = (Variable.Key)array[0];
////                updateVar(key, array[1]);
////            }
//        }
//    }
    /**
     * Method to check invariants in this ElectricObject.
     * @exception AssertionError if invariants are not valid
     */
    protected void check() {
    }
}
