/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Poly.java
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
package com.sun.electric.database.geometry;

import com.sun.electric.database.CellTree;
import com.sun.electric.database.ImmutableArcInst;
import com.sun.electric.database.ImmutableNodeInst;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.EditWindow0;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.technology.AbstractShapeBuilder;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.TechPool;
import com.sun.electric.tool.Job;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.Orientation;

import java.awt.Font;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Class to define a polygon of points.
 */
public class Poly extends PolyBase {

    public static final Poly[] NULL_ARRAY = {};
    public static final boolean NEWTEXTTREATMENT = true;
    /** when not null, use this graphics */
    private EGraphics graphicsOverride;
    /** the string (if of type TEXT) */
    private String string;
    /** the text descriptor (if of type TEXT) */
    private TextDescriptor descript;
    /** the ElectricObject/Variable (if of type TEXT) */
    private DisplayedText dt;

    /**
     * The constructor creates a new Poly given an array of points.
     * @param points the array of coordinates.
     */
    public Poly(Point... points) {
        super(points);
    }

    /**
     * The constructor creates a new Poly given an array of points.
     * @param points the array of coordinates.
     */
    public Poly(EPoint... points) {
        this(convertPoints(points));
    }

    private static Point[] convertPoints(EPoint[] points) {
        Point[] newPoints = new Point[points.length];
        for (int i = 0; i < points.length; i++) {
            Point2D p = points[i];
            newPoints[i] = p instanceof Point ? (Point) p : from(p);
        }
        return newPoints;
    }

    /**
     * The constructor creates a new Poly that describes a rectangle.
     * @param cX the center X coordinate of the rectangle.
     * @param cY the center Y coordinate of the rectangle.
     * @param width the width of the rectangle.
     * @param height the height of the rectangle.
     */
    public Poly(double cX, double cY, double width, double height) {
        super(cX, cY, width, height);
    }

    /**
     * The constructor creates a new Poly that describes a rectangle.
     * @param rect the Rectangle2D of the rectangle.
     */
    public Poly(Rectangle2D rect) {
        super(rect);
    }

//    /**
//	 * Method to return the EGraphics which should be used to draw this Poly.
//     * It is either layer's default graphics or graphics override, if any.
//	 * @return the EGraphics to draw this Poly.
//	 */
//    public EGraphics getGraphics() {
//        if (graphicsOverride != null)
//            return graphicsOverride;
//        Layer layer = getLayer();
//        if (layer != null)
//            return layer.getGraphics();
//        return null;
//    }
    /**
     * Method to return the EGraphics which overrides default EGraphics
     * for Poly's Layer. If null, use default Layer's graphics.
     * @return the String associated with this Poly.
     */
    public EGraphics getGraphicsOverride() {
        return graphicsOverride;
    }

    /**
     * Method to set the EGraphics which overrides default EGraphics
     * for Poly's Layer. If null, use default Layer's graphics.
     * @param graphics graphics override
     */
    public void setGraphicsOverride(EGraphics graphics) {
        graphicsOverride = graphics;
    }

    /**
     * Method to return the String associated with this Poly.
     * This only applies to text Polys which display a message.
     * @return the String associated with this Poly.
     */
    public String getString() {
        return string;
    }

    /**
     * Method to set the String associated with this Poly.
     * This only applies to text Polys which display a message.
     * @param string the String associated with this Poly.
     */
    public void setString(String string) {
        this.string = string;
    }

    /**
     * Method to return the Text Descriptor associated with this Poly.
     * This only applies to text Polys which display a message.
     * Only the size, face, italic, bold, and underline fields are relevant.
     * @return the Text Descriptor associated with this Poly.
     */
    public TextDescriptor getTextDescriptor() {
        return descript;
    }

    /**
     * Method to set the Text Descriptor associated with this Poly.
     * This only applies to text Polys which display a message.
     * Only the size, face, italic, bold, and underline fields are relevant.
     * @param descript the Text Descriptor associated with this Poly.
     */
    public void setTextDescriptor(TextDescriptor descript) {
        this.descript = descript;
    }

    /**
     * Method to return the DisplayedText associated with this Poly.
     * This only applies to text Polys which display a message.
     * @return the DisplayedText associated with this Poly.
     */
    public DisplayedText getDisplayedText() {
        return dt;
    }

    /**
     * Method to set the DisplayedText associated with this Poly.
     * This only applies to text Polys which display a message.
     * @param dt the DisplayedText associated with this Poly.
     */
    public void setDisplayedText(DisplayedText dt) {
        this.dt = dt;
    }

    /**
     * Method to transformed the points in this Poly.
     * @param af transformation to apply.
     */
    public void transform(FixpTransform af) {
        // Nothing to do
        if (af.getType() == FixpTransform.TYPE_IDENTITY) {
            return;
        }

        // special case for text
        if (getStyle().isText() && descript != null) {
            if (NEWTEXTTREATMENT) {
                setStyle(getStyle().transformAnchorOfType(af));

            } else if ((af.getType() & FixpTransform.TYPE_QUADRANT_ROTATION) != 0) {
                // for quadrant rotations, rotate the text angle too
                double m00 = af.getScaleX();
                double m01 = af.getShearX();
                double m11 = af.getScaleY();
                double m10 = af.getShearY();
                if (m00 == 0 && m11 == 0) {
                    // a 90/270 rotation
                    if (m01 > m10) {
                        // 270-degree rotation
                        int ang = descript.getRotation().getAngle();
                        TextDescriptor.Rotation r = TextDescriptor.Rotation.getRotation((ang + 270) % 360);
                        descript = descript.withRotation(r);
                    } else {
                        // 90-degree rotation
                        int ang = descript.getRotation().getAngle();
                        TextDescriptor.Rotation r = TextDescriptor.Rotation.getRotation((ang + 90) % 360);
                        descript = descript.withRotation(r);
                    }
                }
            }
        }
        super.transform(af);
    }
    private static final int[] extendFactor = {0,
        11459, 5729, 3819, 2864, 2290, 1908, 1635, 1430, 1271, 1143,
        1039, 951, 878, 814, 760, 712, 669, 631, 598, 567,
        540, 514, 492, 470, 451, 433, 417, 401, 387, 373,
        361, 349, 338, 327, 317, 308, 299, 290, 282, 275,
        267, 261, 254, 248, 241, 236, 230, 225, 219, 214,
        210, 205, 201, 196, 192, 188, 184, 180, 177, 173,
        170, 166, 163, 160, 157, 154, 151, 148, 146, 143,
        140, 138, 135, 133, 130, 128, 126, 123, 121, 119,
        117, 115, 113, 111, 109, 107, 105, 104, 102, 100};

    /**
     * Method to return the amount that an arc end should extend, given its width and extension factor.
     * @param width the width of the arc.
     * @param extend the extension factor (from 0 to 90).
     * @return the extension (from 0 to half of the width).
     */
    public static double getExtendFactor(double width, int extend) {
        return extend <= 0 || extend >= 90 ? width * 0.5 : width * 50 / extendFactor[extend];
    }

    /**
     * Method to construct a Poly for an arc with a given length, width, angle, endpoint, and extension.
     * @param len the length of the arc.
     * @param wid the width of the arc.
     * @param angle the angle of the arc.
     * @param endH the head end of the arc.
     * @param extendH the head end extension distance of the arc.
     * @param endT the tail end of the arc.
     * @param extendT the tail end extension distance of the arc.
     * @param style the style of the polygon (filled, opened, etc.)
     * @return a Poly describing the outline of the arc.
     */
    public static Poly makeEndPointPoly(double len, double wid, int angle, Point2D endH, double extendH,
            Point2D endT, double extendT, Poly.Type style) {
        double w2 = wid / 2;
        double x1 = endH.getX();
        double y1 = endH.getY();
        double x2 = endT.getX();
        double y2 = endT.getY();
        Point[] points = null;

        // somewhat simpler if rectangle is manhattan
        if (angle == 900 || angle == 2700) {
            if (angle == 900) //			if (y1 > y2)
            {
                double temp = y1;
                y1 = y2;
                y2 = temp;
                temp = extendH;
                extendH = extendT;
                extendT = temp;
            }
            points = new Point[]{
                fromLambda(x1 - w2, y1 - extendH),
                fromLambda(x1 + w2, y1 - extendH),
                fromLambda(x2 + w2, y2 + extendT),
                fromLambda(x2 - w2, y2 + extendT)};
        } else if (angle == 0 || angle == 1800) {
            if (angle == 0) //			if (x1 > x2)
            {
                double temp = x1;
                x1 = x2;
                x2 = temp;
                temp = extendH;
                extendH = extendT;
                extendT = temp;
            }
            points = new Point[]{
                fromLambda(x1 - extendH, y1 - w2),
                fromLambda(x1 - extendH, y1 + w2),
                fromLambda(x2 + extendT, y2 + w2),
                fromLambda(x2 + extendT, y2 - w2)};
        } else {
            // nonmanhattan arcs cannot have zero length so re-compute it
            if (len == 0) {
                len = endH.distance(endT);
            }
            double xextra, yextra, xe1, ye1, xe2, ye2;
            if (len == 0) {
                double sa = DBMath.sin(angle);
                double ca = DBMath.cos(angle);
                xe1 = x1 - ca * extendH;
                ye1 = y1 - sa * extendH;
                xe2 = x2 + ca * extendT;
                ye2 = y2 + sa * extendT;
                xextra = ca * w2;
                yextra = sa * w2;
            } else {
                // work out all the math for nonmanhattan arcs
                xe1 = x1 - extendH * (x2 - x1) / len;
                ye1 = y1 - extendH * (y2 - y1) / len;
                xe2 = x2 + extendT * (x2 - x1) / len;
                ye2 = y2 + extendT * (y2 - y1) / len;

                // now compute the corners
                xextra = w2 * (x2 - x1) / len;
                yextra = w2 * (y2 - y1) / len;
            }
            points = new Point[]{
                fromLambda(yextra + xe1, ye1 - xextra),
                fromLambda(xe1 - yextra, xextra + ye1),
                fromLambda(xe2 - yextra, xextra + ye2),
                fromLambda(yextra + xe2, ye2 - xextra)};
        }
        if (wid != 0 && style.isOpened()) {
            points = new Point[]{points[0], points[1], points[2], points[3], points[0]};
        }
        Poly poly = new Poly(points);
        poly.setStyle(style);
        return poly;
    }

    /**
     * Method to convert text Polys to their precise bounds in a given window.
     * @param wnd the window.
     * @param eObj the ElectricObject on which this text resides.
     * If that ElectricObject is a NodeInst and the node is rotated, it affects the text anchor point.
     * @return true if the text is too small to display.
     */
    public boolean setExactTextBounds(EditWindow0 wnd, ElectricObject eObj) {
        if (getString() == null) {
            return true;
        }
        String theString = getString().trim();
        if (theString.length() == 0) {
            return true;
        }
        int numLines = 1;
        if (dt != null) {
            Variable var = dt.getVariable();
            if (var != null) {
                numLines = var.getLength();
                if (numLines > 1) {
                    Object[] objList = (Object[]) var.getObject();
                    for (int i = 0; i < numLines; i++) {
                        // empty line
                        if (objList[i] == null) {
                            continue;
                        }
                        String str = objList[i].toString();
                        if (str.length() > theString.length()) {
                            theString = str;
                        }
                    }
                }
            }
        }

        Type style = getStyle();
        style = rotateType(style, eObj);
        Font font = descript != null ? descript.getFont(wnd, 0) : TextDescriptor.getDefaultFont(wnd);
        if (font == null) {
            UserInterface ui = Job.getUserInterface();
            double size = ui.getDefaultTextSize();
            if (descript != null) {
                size = descript.getTrueSize(wnd);
            }
            size = size / wnd.getScale();
            if (size <= 0) {
                size = 1;
            }
            double cX = getBounds2D().getCenterX();
            double cY = getBounds2D().getCenterY();
            double sizeIndent = size / 4;
            double fakeWidth = theString.length() * size * 0.75;
            Point2D pt = getTextCorner(style, cX, cY, fakeWidth, size);
            cX = pt.getX();
            cY = pt.getY();
            points = new Point[]{
                fromLambda(cX, cY + sizeIndent),
                fromLambda(cX + fakeWidth, cY + sizeIndent),
                fromLambda(cX + fakeWidth, cY + size - sizeIndent),
                fromLambda(cX, cY + size - sizeIndent)};
            this.bounds = null;
            return false;
        }
        Rectangle2D bounds = getBounds2D();
        double lX = bounds.getMinX();
        double hX = bounds.getMaxX();
        double lY = bounds.getMinY();
        double hY = bounds.getMaxY();

        Rectangle2D glyphBounds = wnd.getGlyphBounds(theString, font);
        // adjust to place text in the center
        double textScale = getTextScale(wnd, glyphBounds, style, lX, hX, lY, hY);
        double screenWidth = glyphBounds == null ? 1.0 : glyphBounds.getWidth();
        double screenHeight = font.getSize();
        if (screenHeight == 1) {
            UserInterface ui = Job.getUserInterface();
            double size = ui.getDefaultTextSize();
            if (descript != null) size = descript.getTrueSize(wnd);
            if (size <= 0) size = 1;
        	screenWidth = theString.length() * size * 0.75;
        }
        double dbWidth = screenWidth * textScale;
        double dbHeight = screenHeight * textScale;
        double cX = (lX + hX) / 2;
        double cY = (lY + hY) / 2;
        Point2D corner = getTextCorner(style, cX, cY, dbWidth, dbHeight);
        cX = corner.getX();
        cY = corner.getY();
        dbHeight *= numLines;
        switch (descript.getRotation().getIndex()) {
            case 1:		// rotate 90 counterclockwise
                double saveWidth = dbWidth;
                dbWidth = -dbHeight;
                dbHeight = saveWidth;
                break;
            case 2:		// rotate 180
            	dbWidth = -dbWidth;
            	dbHeight = -dbHeight;
                break;
            case 3:		// rotate 90 clockwise
                double saveHeight = dbHeight;
                dbHeight = -dbWidth;
                dbWidth = saveHeight;
                break;
        }
        points = new Point[]{
            fromLambda(cX, cY),
            fromLambda(cX + dbWidth, cY),
            fromLambda(cX + dbWidth, cY + dbHeight),
            fromLambda(cX, cY + dbHeight)};
        this.bounds = null;
        return false;
    }

    /**
     * Method to return the coordinates of the lower-left corner of text in a window.
     * @param style the anchor information for the text.
     * @param cX the center X bound of the polygon containing the text.
     * @param cY the center Y bound of the polygon containing the text.
     * @param scaledWidth the width of the polygon containing the text.
     * @param scaledHeight the height of the polygon containing the text.
     * @return the coordinates of the lower-left corner of the text.
     */
    private Point2D getTextCorner(Poly.Type style, double cX, double cY, double scaledWidth, double scaledHeight) {
        double offX = 0, offY = 0;
        if (style == Type.TEXTCENT || style == Type.TEXTBOX) {
            offX = -scaledWidth / 2;
            offY = -scaledHeight / 2;
        } else if (style == Type.TEXTTOP) {
            offX = -scaledWidth / 2;
            offY = -scaledHeight;
        } else if (style == Type.TEXTBOT) {
            offX = -scaledWidth / 2;
        } else if (style == Type.TEXTLEFT) {
            offY = -scaledHeight / 2;
        } else if (style == Type.TEXTRIGHT) {
            offX = -scaledWidth;
            offY = -scaledHeight / 2;
        } else if (style == Type.TEXTTOPLEFT) {
            offY = -scaledHeight;
        } else if (style == Type.TEXTBOTLEFT) {
        } else if (style == Type.TEXTTOPRIGHT) {
            offX = -scaledWidth;
            offY = -scaledHeight;
        } else if (style == Type.TEXTBOTRIGHT) {
            offX = -scaledWidth;
//		} if (style == Poly.Type.TEXTBOX)
//		{
//			offX = -(textWidth * textScale) / 2;
//			offY = -(textHeight * textScale) / 2;
        }
        int rotation = getTextDescriptor().getRotation().getIndex();
        if (rotation != 0) {
            double saveOffX = offX;
            switch (rotation) {
                case 1:
                    offX = -offY;
                    offY = saveOffX;
                    break;
                case 2:
                    offX = -offX;
                    offY = -offY;
                    break;
                case 3:
                    offX = offY;
                    offY = -saveOffX;
                    break;
            }
        }
        return new Point2D.Double(cX + offX, cY + offY);
    }

    /**
     * Returns new instance of Poly builder to build shapes in lambda units.
     * @return new instance of Poly builder.
     */
    public static Builder newLambdaBuilder() {
        return new Builder(true);
    }

    /**
     * Returns thread local instance of Poly builder to build shapes in lambda units.
     * @return thread local instance of Poly builder.
     */
    public static Builder threadLocalLambdaBuilder() {
        return threadLocalLambdaBuilder.get();
    }
    private static ThreadLocal<Poly.Builder> threadLocalLambdaBuilder = new ThreadLocal<Poly.Builder>() {

        @Override
        protected Poly.Builder initialValue() {
            return new Builder(false);
        }
    };

    /**
     * This class builds shapes of nodes and arcs in lambda units as Poly arrays.
     */
    public static class Builder extends AbstractShapeBuilder {

        private boolean isChanging;
        private final ArrayList<Poly> lastPolys = new ArrayList<Poly>();

        private Builder(boolean rotateNodes) {
            super(rotateNodes);
        }

        /**
         * Returns the polygons that describe node "ni".
         * @param ni the NodeInst that is being described.
         * The prototype of this NodeInst must be a PrimitiveNode and not a Cell.
         * @return an iterator on Poly objects that describes this NodeInst graphically.
         */
        public Iterator<Poly> getShape(NodeInst ni) {
            isChanging = true;
            setup(ni.getCellBackup(), null, false, true, false, null);
            lastPolys.clear();
            ((PrimitiveNode)ni.getProto()).genShape(this, ni.getD());
            isChanging = false;
            return lastPolys.iterator();
        }

        /**
         * Returns the polygons that describe arc "ai".
         * @param ni the NodeInst that is being described.
         * @return an array of Poly objects that describes this ArcInst graphically.
         */
        public Poly[] getShapeArray(NodeInst ni, boolean electrical, boolean reasonable, Layer.Function.Set onlyTheseLayers) {
            isChanging = true;
            setup(ni.getCellBackup(), null, electrical, !electrical, reasonable, onlyTheseLayers);
            lastPolys.clear();
            ((PrimitiveNode)ni.getProto()).genShape(this, ni.getD());
            if (lastPolys.isEmpty()) {
                isChanging = false;
                return Poly.NULL_ARRAY;
            }
            Poly[] polys = lastPolys.toArray(new Poly[lastPolys.size()]);
            isChanging = false;
            return polys;
        }

        public Poly getShape(NodeInst ni, PrimitivePort pp) {
            isChanging = true;
//            setup(ni.getCellBackup(), null, false, true, false, null);
            setup((TechPool)null);
            lastPolys.clear();
            genShapeOfPort(ni.getD(), pp);
            assert lastPolys.size() == 1;
            Poly poly = lastPolys.get(0);
            isChanging = false;
            poly.setLayer(null);
            return poly;
        }

        public Poly getShape(ImmutableNodeInst n, PrimitivePort pp) {
            isChanging = true;
            setup((TechPool)null);
            lastPolys.clear();
            genShapeOfPort(n, pp);
            assert lastPolys.size() == 1;
            Poly poly = lastPolys.get(0);
            isChanging = false;
            poly.setLayer(null);
            return poly;
        }

        public Poly getShape(CellTree cellTree, ImmutableNodeInst n, PrimitivePort pp, Point2D selectPt) {
            isChanging = true;
            setup(cellTree, null, false, true, false, null);
            lastPolys.clear();
            genShapeOfPort(n, pp, selectPt);
            assert lastPolys.size() == 1;
            Poly poly = lastPolys.get(0);
            isChanging = false;
            poly.setLayer(null);
            return poly;
        }

        /**
         * Returns the polygons that describe arc "ai".
         * @param ai the ArcInst that is being described.
         * @return an iterator on Poly objects that describes this ArcInst graphically.
         */
        public Iterator<Poly> getShape(ArcInst ai) {
            isChanging = true;
            setup(ai.getParent());
            lastPolys.clear();
            genShapeOfArc(ai.getD());
            isChanging = false;
            return lastPolys.iterator();
        }

        /**
         * Returns the polygons that describe arc "ai".
         * @param ai the ArcInst that is being described.
         * @return an array of Poly objects that describes this ArcInst graphically.
         */
        public Poly[] getShapeArray(ArcInst ai, Layer.Function.Set onlyTheseLayers) {
            isChanging = true;
            setup(ai.getParent().backup(), null, false, true, false, onlyTheseLayers);
            lastPolys.clear();
            genShapeOfArc(ai.getD());
            if (lastPolys.isEmpty()) {
                isChanging = false;
                return Poly.NULL_ARRAY;
            }
            Poly[] polys = lastPolys.toArray(new Poly[lastPolys.size()]);
            isChanging = false;
            return polys;
        }

        /**
         * Method to create a Poly object that describes an ImmutableArcInst.
         * The ImmutableArcInst is described by its width and style.
         * @param a an ImmutableArcInst
         * @param gridWidth the width of the Poly in grid units.
         * @param style the style of the ArcInst.
         * @return a Poly that describes the ArcInst.
         */
        public Poly makePoly(ImmutableArcInst a, long gridWidth, Poly.Type style) {
            isChanging = true;
            lastPolys.clear();
            getTechPool().getArcProto(a.protoId).makeGridPoly(this, a, gridWidth, style, null, null);
            isChanging = false;
            if (lastPolys.isEmpty()) {
                return null;
            }
            Poly poly = lastPolys.get(0);
            return poly;
        }

        @Override
        public void addPoly(int numPoints, Poly.Type style, Layer layer, EGraphics graphicsOverride, PrimitivePort pp) {
            assert isChanging;
            Point[] points = new Point[numPoints];
            for (int i = 0; i < numPoints; i++) {
                points[i] = fromFixp(coords[i * 2], coords[i * 2 + 1]);
            }
            Poly poly = new Poly(points);
            poly.setStyle(style);
            poly.setLayer(layer);
            poly.setGraphicsOverride(graphicsOverride);
            poly.setPort(pp);
            lastPolys.add(poly);
        }

        @Override
        public void addTextPoly(int numPoints, Poly.Type style, Layer layer, PrimitivePort pp, String message, TextDescriptor descriptor) {
            assert isChanging;
            Point[] points = new Point[numPoints];
            for (int i = 0; i < numPoints; i++) {
                points[i] = fromFixp(coords[i * 2], coords[i * 2 + 1]);
            }
            Poly poly = new Poly(points);
            poly.setStyle(style);
            poly.setLayer(layer);
            poly.setPort(pp);
            poly.setString(message);
            poly.setTextDescriptor(descriptor);
            lastPolys.add(poly);
        }

        @Override
        public void addBox(Layer layer) {
            assert isChanging;
            long xl = coords[0];
            long yl = coords[1];
            long xh = coords[2];
            long yh = coords[3];
            Poly poly = new Poly(fromFixp(xl, yl), fromFixp(xh, yl), fromFixp(xh, yh), fromFixp(xl, yh));
            poly.setStyle(Poly.Type.FILLED);
            poly.setLayer(layer);
            lastPolys.add(poly);
        }
    }

    /**
     * Type is a typesafe enum class that describes the nature of a Poly.
     */
    public static enum Type {
        // ************************ polygons ************************

        /**
         * Describes a closed polygon which is filled in.
         */
        FILLED("filled", false),
        /**
         * Describes a closed polygon with only the outline drawn.
         */
        CLOSED("closed", false),
        /**
         * Describes a closed rectangle with the outline drawn and an "X" drawn through it.
         */
        CROSSED("crossed", false),
        // ************************ lines ************************
        /**
         * Describes an open outline.
         * The last point is not implicitly connected to the first point.
         */
        OPENED("opened", false),
        /**
         * Describes an open outline, drawn with a dotted texture.
         * The last point is not implicitly connected to the first point.
         */
        OPENEDT1("opened-dotted", false),
        /**
         * Describes an open outline, drawn with a dashed texture.
         * The last point is not implicitly connected to the first point.
         */
        OPENEDT2("opened-dashed", false),
        /**
         * Describes an open outline, drawn with thicker lines.
         * The last point is not implicitly connected to the first point.
         */
        OPENEDT3("opened-thick", false),
        /**
         * Describes a vector endpoint pairs, solid.
         * There must be an even number of points in the Poly so that vectors can be drawn from point 0 to 1,
         * then from point 2 to 3, etc.
         */
        VECTORS("vectors", false),
        // ************************ curves ************************
        /**
         * Describes a circle (only the outline is drawn).
         * The first point is the center of the circle and the second point is on the edge, thus defining the radius.
         * This second point should be on the same horizontal level as the radius point to make radius computation easier.
         */
        CIRCLE("circle", false),
        /**
         * Describes a circle, drawn with thick lines (only the outline is drawn).
         * The first point is the center of the circle and the second point is on the edge, thus defining the radius.
         * This second point should be on the same horizontal level as the radius point to make radius computation easier.
         */
        THICKCIRCLE("thick-circle", false),
        /**
         * Describes a filled circle.
         * The first point is the center of the circle and the second point is on the edge, thus defining the radius.
         * This second point should be on the same horizontal level as the radius point to make radius computation easier.
         */
        DISC("disc", false),
        /**
         * Describes an arc of a circle.
         * The first point is the center of the circle, the second point is the start of the arc, and
         * the third point is the end of the arc.
         * The arc will be drawn counter-clockwise from the start point to the end point.
         */
        CIRCLEARC("circle-arc", false),
        /**
         * Describes an arc of a circle, drawn with thick lines.
         * The first point is the center of the circle, the second point is the start of the arc, and
         * the third point is the end of the arc.
         * The arc will be drawn counter-clockwise from the start point to the end point.
         */
        THICKCIRCLEARC("thick-circle-arc", false),
        // ************************ text ************************
        /**
         * Describes text that should be centered about the Poly point.
         * Only one point need be specified.
         */
        TEXTCENT("text-center", true),
        /**
         * Describes text that should be placed so that the Poly point is at the top-center.
         * Only one point need be specified, and the text will be below that point.
         */
        TEXTTOP("text-top", true),
        /**
         * Describes text that should be placed so that the Poly point is at the bottom-center.
         * Only one point need be specified, and the text will be above that point.
         */
        TEXTBOT("text-bottom", true),
        /**
         * Describes text that should be placed so that the Poly point is at the left-center.
         * Only one point need be specified, and the text will be to the right of that point.
         */
        TEXTLEFT("text-left", true),
        /**
         * Describes text that should be placed so that the Poly point is at the right-center.
         * Only one point need be specified, and the text will be to the left of that point.
         */
        TEXTRIGHT("text-right", true),
        /**
         * Describes text that should be placed so that the Poly point is at the upper-left.
         * Only one point need be specified, and the text will be to the lower-right of that point.
         */
        TEXTTOPLEFT("text-topleft", true),
        /**
         * Describes text that should be placed so that the Poly point is at the lower-left.
         * Only one point need be specified, and the text will be to the upper-right of that point.
         * This is the normal starting point for most text.
         */
        TEXTBOTLEFT("text-botleft", true),
        /**
         * Describes text that should be placed so that the Poly point is at the upper-right.
         * Only one point need be specified, and the text will be to the lower-left of that point.
         */
        TEXTTOPRIGHT("text-topright", true),
        /**
         * Describes text that should be placed so that the Poly point is at the lower-right.
         * Only one point need be specified, and the text will be to the upper-left of that point.
         */
        TEXTBOTRIGHT("text-botright", true),
        /**
         * Describes text that is centered in the Poly and must remain inside.
         * If the letters do not fit, a smaller font will be used, and if that still does not work,
         * any letters that cannot fit are not written.
         * The Poly coordinates must define an area for the text to live in.
         */
        TEXTBOX("text-box", true),
        // ************************ miscellaneous ************************
        /**
         * Describes a small cross, drawn at the specified location.
         * Typically there will be only one point in this polygon
         * but if there are more they are averaged and the cross is drawn in the center.
         */
        CROSS("cross", false),
        /**
         * Describes a big cross, drawn at the specified location.
         * Typically there will be only one point in this polygon
         * but if there are more they are averaged and the cross is drawn in the center.
         */
        BIGCROSS("big-cross", false);
        private final String name;
        private final boolean isText;

        private Type(String name, boolean isText) {
            this.name = name;
            this.isText = isText;
        }

        /**
         * Method to tell whether this Poly Style is text.
         * @return true if this Poly Style is text.
         */
        public boolean isText() {
            return isText;
        }

        /**
         * Returns a printable version of this Type.
         * @return a printable version of this Type.
         */
        public String toString() {
            return "Poly.Type " + name;
        }

        /**
         * Method to tell whether this is a style that can draw an opened polygon.
         * @return true if this is a style that can draw an opened polygon.
         */
        public boolean isOpened() {
            if (this == OPENED || this == OPENEDT1 || this == OPENEDT2
                    || this == OPENEDT3 || this == VECTORS) {
                return true;
            }
            return false;
        }

        /**
         * Method to get the "angle" of a style of text.
         * When rotating a node, the anchor point also rotates.
         * To to this elegantly, the Type is converted to an angle, rotated, and then converted back to a Type.
         * @return the angle of this text Type.
         */
        public int getTextAngle() {
            if (this == TEXTLEFT) {
                return 0;
            }
            if (this == TEXTBOTLEFT) {
                return 450;
            }
            if (this == TEXTBOT) {
                return 900;
            }
            if (this == TEXTBOTRIGHT) {
                return 1350;
            }
            if (this == TEXTRIGHT) {
                return 1800;
            }
            if (this == TEXTTOPRIGHT) {
                return 2250;
            }
            if (this == TEXTTOP) {
                return 2700;
            }
            if (this == TEXTTOPLEFT) {
                return 3150;
            }
            return 0;
        }

        /**
         * Method to get a text Type from an angle.
         * When rotating a node, the anchor point also rotates.
         * To to this elegantly, the Type is converted to an angle, rotated, and then converted back to a Type.
         * @param angle of the text anchor.
         * @return a text Type that corresponds to the angle.
         */
        public static Type getTextTypeFromAngle(int angle) {
            switch (angle) {
                case 0:
                    return TEXTLEFT;
                case 450:
                    return TEXTBOTLEFT;
                case 900:
                    return TEXTBOT;
                case 1350:
                    return TEXTBOTRIGHT;
                case 1800:
                    return TEXTRIGHT;
                case 2250:
                    return TEXTTOPRIGHT;
                case 2700:
                    return TEXTTOP;
                case 3150:
                    return TEXTTOPLEFT;
            }
            return TEXTCENT;
        }

        /** cycle through all possible horizontal anchorings without altering the vertical anchoring */
 	    public Poly.Type cycleTextAnchorHoriz()
 	    {
            switch(this) {
                case TEXTLEFT:     return TEXTCENT;
                case TEXTCENT:     return TEXTRIGHT;
                case TEXTRIGHT:    return TEXTLEFT;
                    
                case TEXTBOTLEFT:  return TEXTBOT;
                case TEXTBOT:      return TEXTBOTRIGHT;
                case TEXTBOTRIGHT: return TEXTBOTLEFT;
                    
                case TEXTTOPLEFT:  return TEXTTOP;
                case TEXTTOP:      return TEXTTOPRIGHT;
                case TEXTTOPRIGHT: return TEXTTOPLEFT;
                    
                default:           return this;
            }
 	    }
        
        /** cycle through all possible vertical anchorings without altering the horizontal anchoring */
 	    public Poly.Type cycleTextAnchorVert()
 	    {
            switch(this) {
                case TEXTTOPLEFT:  return TEXTLEFT;
                case TEXTLEFT:     return TEXTBOTLEFT;
                case TEXTBOTLEFT:  return TEXTTOPLEFT;
                    
                case TEXTTOP:      return TEXTCENT;
                case TEXTCENT:     return TEXTBOT;
                case TEXTBOT:      return TEXTTOP;
                    
                case TEXTTOPRIGHT: return TEXTRIGHT;
                case TEXTRIGHT:    return TEXTBOTRIGHT;
                case TEXTBOTRIGHT: return TEXTTOPRIGHT;
                default:           return this;
            }
 	    }

        public Poly.Type rotateTextAnchorIn(TextDescriptor.Rotation rot) {
            Poly.Type newStyle = this;
            if (rot != TextDescriptor.Rotation.ROT0) {
                int angle = getTextAngle();
                if (rot == TextDescriptor.Rotation.ROT90) {
                    angle += 2700;
                } else if (rot == TextDescriptor.Rotation.ROT180) {
                    angle += 1800;
                } else if (rot == TextDescriptor.Rotation.ROT270) {
                    angle += 900;
                }
                newStyle = Poly.Type.getTextTypeFromAngle(angle % 3600);
            }
            return newStyle;
        }

        public Poly.Type rotateTextAnchorOut(TextDescriptor.Rotation rot) {
            Poly.Type newStyle = this;
            if (rot != TextDescriptor.Rotation.ROT0) {
                int angle = getTextAngle();
                if (rot == TextDescriptor.Rotation.ROT90) {
                    angle += 900;
                } else if (rot == TextDescriptor.Rotation.ROT180) {
                    angle += 1800;
                } else if (rot == TextDescriptor.Rotation.ROT270) {
                    angle += 2700;
                }
                newStyle = Poly.Type.getTextTypeFromAngle(angle % 3600);
            }
            return newStyle;
        }

        /**
         * Method to mirror text Type horizontally or vertically
         */
        public Poly.Type mirrorType(boolean horizontal) {
            if (horizontal) {
                switch(this) {
                    case TEXTLEFT:     return TEXTRIGHT;
                    case TEXTRIGHT:    return TEXTLEFT;
                    case TEXTBOTLEFT:  return TEXTBOTRIGHT;
                    case TEXTBOTRIGHT: return TEXTBOTLEFT;
                    case TEXTTOPLEFT:  return TEXTTOPRIGHT;
                    case TEXTTOPRIGHT: return TEXTTOPLEFT;
                    default:           return this;
                }
            } else {
                switch(this) {
                    case TEXTBOTLEFT:  return TEXTTOPLEFT;
                    case TEXTBOT:      return TEXTTOP;
                    case TEXTBOTRIGHT: return TEXTTOPRIGHT;
                    case TEXTTOPLEFT:  return TEXTBOTLEFT;
                    case TEXTTOP:      return TEXTBOT;
                    case TEXTTOPRIGHT: return TEXTBOTRIGHT;
                    default:           return this;
                }
            }
        }
 
        /**
         * Method to transform an anchor according to an affine transformation
         */
        public Poly.Type transformAnchorOfType(AffineTransform af) {
            if (this == TEXTCENT || this == TEXTBOX) return this;
            double m00 = af.getScaleX();
            double m01 = af.getShearX();
            double m11 = af.getScaleY();
            double m10 = af.getShearY();
            Poly.Type ret = this;
            boolean mirrorH = false;
            if ((m00 < 0 & m11 > 0) || (m00 > 0 & m11 < 0) || (m10 < 0 & m01 < 0) || (m10 > 0 & m01 > 0)) {
                mirrorH = true;
                m00 *= -1;
                m01 *= -1;
            }
            if (m10 < m01)                  ret = getTextTypeFromAngle((ret.getTextAngle() + 2700) % 3600);
            else if (m01 < m10)             ret = getTextTypeFromAngle((ret.getTextAngle() +  900) % 3600);
            else if (m00 < 0 && m11 < 0)    ret = getTextTypeFromAngle((ret.getTextAngle() + 1800) % 3600);
            if (mirrorH) ret = ret.mirrorType(true);
            return ret;
        }
 
        /**
         * Method to transform an anchor according to an Orientation
         */
        public Poly.Type transformAnchorOfType(Orientation orient) {
            if (this == TEXTCENT || this == TEXTBOX) return this;
            Poly.Type ret = this;
            ret = getTextTypeFromAngle((ret.getTextAngle() + orient.getAngle()) % 3600);
            if (orient.isXMirrored()) ret = ret.mirrorType(true);
            if (orient.isYMirrored()) ret = ret.mirrorType(false);
            return ret;
        }
    } 
}
