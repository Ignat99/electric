/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: AbstractLayerDrawing.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.redisplay;

import com.sun.electric.database.geometry.EGraphics;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.ECoord;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.Orientation;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 *
 */
public class AbstractLayerDrawing {

    /** the size of the EditWindow */
    final Dimension sz;
    /** the scale of the EditWindow */
    double scale;
    /** the X origin of the cell in display coordinates. */
    double originX;
    /** the Y origin of the cell in display coordinates. */
    double originY;
    /** the scale of the EditWindow */
    private double scale_;
    /** the window scale and pan factor */
    private float factorX, factorY;
    int clipLX, clipHX, clipLY, clipHY;
    ERectangle drawLimitBounds;
    /** temporary objects (saves reallocation) */
    final Point tempPt1 = new Point(), tempPt2 = new Point();
    /** temporary objects (saves reallocation) */
    final Point tempPt3 = new Point(), tempPt4 = new Point();
    final FixpRectangle tempFixpRect = FixpRectangle.fromFixpDiagonal(0, 0, 0, 0);

    /**
     * Constructor creates an offscreen PixelDrawing object.
     * @param sz the size of an offscreen PixelDrawinf object.
     */
    public AbstractLayerDrawing(Dimension sz) {
        this.sz = new Dimension(sz);
//        width = sz.width;
        setClip(null);
    }

    void initOrigin(double scale, int originX, int originY) {
        this.scale = scale;
        scale_ = (float) (scale / DBMath.GRID);

        this.originX = originX;
        this.originY = originY;
        factorX = (float) (-originX / scale_);
        factorY = (float) (originY / scale_);
    }

    void initOrigin(double scale, Point2D offset) {
        this.scale = scale;
        scale_ = (float) (scale / DBMath.GRID);
        this.originX = sz.width / 2 - offset.getX() * scale;
        this.originY = sz.height / 2 + offset.getY() * scale;
        factorX = (float) (offset.getX() * DBMath.GRID - sz.width / 2 / scale_);
        factorY = (float) (offset.getY() * DBMath.GRID + sz.height / 2 / scale_);
    }

    /**
     * @param drawLimitBounds the area in the cell to display (null to show all).
     * @return render bounds in screen coordinates
     */
    Rectangle setClip(ERectangle drawLimitBounds) {
        Rectangle renderBounds = null;
        ERectangle screenDb = ERectangle.fromLambda(-originX / scale, (originY - sz.height) / scale, sz.width / scale, sz.height / scale);
        if (drawLimitBounds != null) {
            this.drawLimitBounds = (ERectangle)screenDb.createIntersection(drawLimitBounds);
            renderBounds = databaseToScreen(drawLimitBounds);
            setClip(Math.max(renderBounds.x, 0), Math.min(renderBounds.x + renderBounds.width, sz.width) - 1,
                    Math.max(renderBounds.y, 0), Math.min(renderBounds.y + renderBounds.height, sz.height) - 1);
        } else {
            this.drawLimitBounds = screenDb;
            setClip(0, sz.width - 1, 0, sz.height - 1);
        }
        return renderBounds;
    }

    private void setClip(int clipLX, int clipHX, int clipLY, int clipHY) {
        this.clipLX = clipLX;
        this.clipHX = clipHX;
        this.clipLY = clipLY;
        this.clipHY = clipHY;
    }

    FixpRectangle getSearchBounds(long oX, long oY, Orientation orient) {
        long fpX = oX << ECoord.FRACTION_BITS;
        long fpY = oY << ECoord.FRACTION_BITS;
        tempFixpRect.setFixp(
                drawLimitBounds.getFixpMinX() - fpX, drawLimitBounds.getFixpMinY() - fpY,
                drawLimitBounds.getFixpMaxX() - fpX, drawLimitBounds.getFixpMaxY() - fpY);
        orient.inverse().rectangleBounds(tempFixpRect, EPoint.ORIGIN, tempFixpRect);
        return tempFixpRect;
    }

    // ************************************* BOX DRAWING *************************************
    /**
     * Method to draw a box on the off-screen buffer.
     */
    public void drawBox(long gridLX, long gridLY, long gridHX, long gridHY, ERaster raster) {
        gridToScreen(gridLX, gridLY, tempPt1);
        gridToScreen(gridHX, gridHY, tempPt2);
        int lX = tempPt1.x;
        int hY = tempPt1.y;
        int hX = tempPt2.x;
        int lY = tempPt2.y;
        if (lX < clipLX) {
            lX = clipLX;
        }
        if (hX > clipHX) {
            hX = clipHX;
        }
        if (lY < clipLY) {
            lY = clipLY;
        }
        if (hY > clipHY) {
            hY = clipHY;
        }
        if (lX > hX || lY > hY) {
            return;
        }
//        boxDisplayCount++;
        EGraphics.Outline o = raster.getOutline();
        if (lY == hY) {
            if (lX == hX) {
                if (o == null) {
                    raster.fillPoint(lX, lY);
                } else {
                    raster.drawPoint(lX, lY);
                }
            } else {
                if (o == null) {
                    raster.fillHorLine(lY, lX, hX);
                } else {
                    raster.drawHorLine(lY, lX, hX);
                }
            }
            return;
        }
        if (lX == hX) {
            if (o == null) {
                raster.fillVerLine(lX, lY, hY);
            } else {
                raster.drawVerLine(lX, lY, hY);
            }
            return;
        }
        raster.fillBox(lX, hX, lY, hY);
        if (o == null) {
            return;
        }
        if (o.isSolidPattern()) {
            raster.drawVerLine(lX, lY, hY);
            raster.drawHorLine(hY, lX, hX);
            raster.drawVerLine(hX, lY, hY);
            raster.drawHorLine(lY, lX, hX);
            if (o.getThickness() != 1) {
                for (int i = 1; i < o.getThickness(); i++) {
                    if (lX + i <= clipHX) {
                        raster.drawVerLine(lX + i, lY, hY);
                    }
                    if (hY - i >= clipLX) {
                        raster.drawHorLine(hY - i, lX, hX);
                    }
                    if (hX - i >= clipLY) {
                        raster.drawVerLine(hX - i, lY, hY);
                    }
                    if (lY + i <= clipHY) {
                        raster.drawHorLine(lY + i, lX, hX);
                    }
                }
            }
        } else {
            int pattern = o.getPattern();
            int len = o.getLen();
            drawVerOutline(lX, lY, hY, pattern, len, raster);
            drawHorOutline(hY, lX, hX, pattern, len, raster);
            drawVerOutline(hX, lY, hY, pattern, len, raster);
            drawHorOutline(lY, lX, hX, pattern, len, raster);
            if (o.getThickness() != 1) {
                for (int i = 1; i < o.getThickness(); i++) {
                    if (lX + i <= clipHX) {
                        drawVerOutline(lX + i, lY, hY, pattern, len, raster);
                    }
                    if (hY - i >= clipLX) {
                        drawHorOutline(hY - i, lX, hX, pattern, len, raster);
                    }
                    if (hX - i >= clipLY) {
                        drawVerOutline(hX - i, lY, hY, pattern, len, raster);
                    }
                    if (lY + i <= clipHY) {
                        drawHorOutline(lY + i, lX, hX, pattern, len, raster);
                    }
                }
            }
        }
    }

    private static void drawHorOutline(int y, int lX, int hX, int pattern, int len, ERaster raster) {
        int i = 0;
        for (int x = lX; x <= hX; x++) {
            if ((pattern & (1 << i)) != 0) {
                raster.drawPoint(x, y);
            }
            i++;
            if (i == len) {
                i = 0;
            }
        }
    }

    private static void drawVerOutline(int x, int lY, int hY, int pattern, int len, ERaster raster) {
        int i = 0;
        for (int y = lY; y <= hY; y++) {
            if ((pattern & (1 << i)) != 0) {
                raster.drawPoint(x, y);
            }
            i++;
            if (i == len) {
                i = 0;
            }
        }
    }

    // ************************************* LINE DRAWING *************************************
    /**
     * Method to draw a line on the off-screen buffer.
     */
    public void drawLine(long gridX1, long gridY1, long gridX2, long gridY2, int texture, ERaster raster) {
        gridToScreen(gridX1, gridY1, tempPt1);
        gridToScreen(gridX2, gridY2, tempPt2);
        drawLine(tempPt1, tempPt2, texture, raster);
    }

    /**
     * Method to draw a line on the off-screen buffer.
     */
    private void drawLine(Point pt1, Point pt2, int texture, ERaster raster) {
        // first clip the line
        if (GenMath.clipLine(pt1, pt2, 0, sz.width - 1, 0, sz.height - 1)) {
            return;
        }

        // now draw with the proper line type
        switch (texture) {
            case 0:
                drawSolidLine(pt1.x, pt1.y, pt2.x, pt2.y, raster);
                break;
            case 1:
                drawPatLine(pt1.x, pt1.y, pt2.x, pt2.y, 0x88, 8, raster);
                break;
            case 2:
                drawPatLine(pt1.x, pt1.y, pt2.x, pt2.y, 0xE7, 8, raster);
                break;
            case 3:
                drawThickLine(pt1.x, pt1.y, pt2.x, pt2.y, raster);
                break;
        }
    }

    public void drawCross(long gridX, long gridY, int size, ERaster raster) {
        gridToScreen(gridX, gridY, tempPt1);
        int cX = tempPt1.x;
        int cY = tempPt1.y;
        if (clipLY <= cY && cY <= clipHY) {
            int lX = Math.max(clipLX, cX - size);
            int hX = Math.min(clipHX, cX + size);
            if (lX <= hX) {
                raster.drawHorLine(cY, lX, hX);
            }
        }
        if (clipLX <= cX && cX <= clipHX) {
            int lY = Math.max(clipLY, cY - size);
            int hY = Math.min(clipHY, cY + size);
            if (lY <= hY) {
                raster.drawVerLine(cX, lY, hY);
            }
        }
    }

    private void drawSolidLine(int x1, int y1, int x2, int y2, ERaster raster) {
        // initialize the Bresenham algorithm
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        if (dx > dy) {
            // initialize for lines that increment along X
            int incr1 = 2 * dy;
            int incr2 = 2 * (dy - dx);
            int d = incr2;
            int x, y, xend, yend, yincr;
            if (x1 > x2) {
                x = x2;
                y = y2;
                xend = x1;
                yend = y1;
            } else {
                x = x1;
                y = y1;
                xend = x2;
                yend = y2;
            }
            if (yend < y) {
                yincr = -1;
            } else {
                yincr = 1;
            }
            raster.drawPoint(x, y);

            // draw line that increments along X
            while (x < xend) {
                x++;
                if (d < 0) {
                    d += incr1;
                } else {
                    y += yincr;
                    d += incr2;
                }
                raster.drawPoint(x, y);
            }
        } else {
            // initialize for lines that increment along Y
            int incr1 = 2 * dx;
            int incr2 = 2 * (dx - dy);
            int d = incr2;
            int x, y, xend, yend, xincr;
            if (y1 > y2) {
                x = x2;
                y = y2;
                xend = x1;
                yend = y1;
            } else {
                x = x1;
                y = y1;
                xend = x2;
                yend = y2;
            }
            if (xend < x) {
                xincr = -1;
            } else {
                xincr = 1;
            }
            raster.drawPoint(x, y);

            // draw line that increments along X
            while (y < yend) {
                y++;
                if (d < 0) {
                    d += incr1;
                } else {
                    x += xincr;
                    d += incr2;
                }
                raster.drawPoint(x, y);
            }
        }
    }

    private void drawOutline(int x1, int y1, int x2, int y2, int pattern, int len, ERaster raster) {
        tempPt3.x = x1;
        tempPt3.y = y1;
        tempPt4.x = x2;
        tempPt4.y = y2;

        // first clip the line
        if (GenMath.clipLine(tempPt3, tempPt4, 0, sz.width - 1, 0, sz.height - 1)) {
            return;
        }

        drawPatLine(tempPt3.x, tempPt3.y, tempPt4.x, tempPt4.y, pattern, len, raster);
    }

    private void drawPatLine(int x1, int y1, int x2, int y2, int pattern, int len, ERaster raster) {
        // initialize counter for line style
        int i = 0;

        // initialize the Bresenham algorithm
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        if (dx > dy) {
            // initialize for lines that increment along X
            int incr1 = 2 * dy;
            int incr2 = 2 * (dy - dx);
            int d = incr2;
            int x, y, xend, yend, yincr;
            if (x1 > x2) {
                x = x2;
                y = y2;
                xend = x1;
                yend = y1;
            } else {
                x = x1;
                y = y1;
                xend = x2;
                yend = y2;
            }
            if (yend < y) {
                yincr = -1;
            } else {
                yincr = 1;
            }
            raster.drawPoint(x, y);

            // draw line that increments along X
            while (x < xend) {
                x++;
                if (d < 0) {
                    d += incr1;
                } else {
                    y += yincr;
                    d += incr2;
                }
                i++;
                if (i == len) {
                    i = 0;
                }
                if ((pattern & (1 << i)) == 0) {
                    continue;
                }
                raster.drawPoint(x, y);
            }
        } else {
            // initialize for lines that increment along Y
            int incr1 = 2 * dx;
            int incr2 = 2 * (dx - dy);
            int d = incr2;
            int x, y, xend, yend, xincr;
            if (y1 > y2) {
                x = x2;
                y = y2;
                xend = x1;
                yend = y1;
            } else {
                x = x1;
                y = y1;
                xend = x2;
                yend = y2;
            }
            if (xend < x) {
                xincr = -1;
            } else {
                xincr = 1;
            }
            raster.drawPoint(x, y);

            // draw line that increments along X
            while (y < yend) {
                y++;
                if (d < 0) {
                    d += incr1;
                } else {
                    x += xincr;
                    d += incr2;
                }
                i++;
                if (i == len) {
                    i = 0;
                }
                if ((pattern & (1 << i)) == 0) {
                    continue;
                }
                raster.drawPoint(x, y);
            }
        }
    }

    private void drawThickLine(int x1, int y1, int x2, int y2, ERaster raster) {
        // initialize the Bresenham algorithm
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        if (dx > dy) {
            // initialize for lines that increment along X
            int incr1 = 2 * dy;
            int incr2 = 2 * (dy - dx);
            int d = incr2;
            int x, y, xend, yend, yincr;
            if (x1 > x2) {
                x = x2;
                y = y2;
                xend = x1;
                yend = y1;
            } else {
                x = x1;
                y = y1;
                xend = x2;
                yend = y2;
            }
            if (yend < y) {
                yincr = -1;
            } else {
                yincr = 1;
            }
            drawThickPoint(x, y, raster);

            // draw line that increments along X
            while (x < xend) {
                x++;
                if (d < 0) {
                    d += incr1;
                } else {
                    y += yincr;
                    d += incr2;
                }
                drawThickPoint(x, y, raster);
            }
        } else {
            // initialize for lines that increment along Y
            int incr1 = 2 * dx;
            int incr2 = 2 * (dx - dy);
            int d = incr2;
            int x, y, xend, yend, xincr;
            if (y1 > y2) {
                x = x2;
                y = y2;
                xend = x1;
                yend = y1;
            } else {
                x = x1;
                y = y1;
                xend = x2;
                yend = y2;
            }
            if (xend < x) {
                xincr = -1;
            } else {
                xincr = 1;
            }
            drawThickPoint(x, y, raster);

            // draw line that increments along X
            while (y < yend) {
                y++;
                if (d < 0) {
                    d += incr1;
                } else {
                    x += xincr;
                    d += incr2;
                }
                drawThickPoint(x, y, raster);
            }
        }
    }

    // ************************************* POLYGON DRAWING *************************************
    private static class PolySeg {

        private int fx, fy, tx, ty, direction, increment;
        private PolySeg nextedge;
        private PolySeg nextactive;
    }

    /**
     * Method to draw a polygon on the off-screen buffer.
     */
    void drawPolygon(long gridOX, long gridOY, EPoint[] gridPoints, ERaster raster) {
        Point[] intPoints = new Point[gridPoints.length];
        for (int i = 0; i < gridPoints.length; i++) {
            intPoints[i] = new Point();
            gridToScreen(gridPoints[i].getGridX() + gridOX, gridPoints[i].getGridY() + gridOY, intPoints[i]);
        }
        Point[] points = GenMath.clipPoly(intPoints, clipLX, clipHX, clipLY, clipHY);
        // fill in internal structures
        PolySeg edgelist = null;
        PolySeg[] polySegs = new PolySeg[points.length];
        for (int i = 0; i < points.length; i++) {
            polySegs[i] = new PolySeg();
            if (i == 0) {
                polySegs[i].fx = points[points.length - 1].x;
                polySegs[i].fy = points[points.length - 1].y;
            } else {
                polySegs[i].fx = points[i - 1].x;
                polySegs[i].fy = points[i - 1].y;
            }
            polySegs[i].tx = points[i].x;
            polySegs[i].ty = points[i].y;
        }
        for (int i = 0; i < points.length; i++) {
            // compute the direction of this edge
            int j = polySegs[i].ty - polySegs[i].fy;
            if (j > 0) {
                polySegs[i].direction = 1;
            } else if (j < 0) {
                polySegs[i].direction = -1;
            } else {
                polySegs[i].direction = 0;
            }

            // compute the X increment of this edge
            if (j == 0) {
                polySegs[i].increment = 0;
            } else {
                polySegs[i].increment = polySegs[i].tx - polySegs[i].fx;
                if (polySegs[i].increment != 0) {
                    polySegs[i].increment =
                            (polySegs[i].increment * 65536 - j + 1) / j;
                }
            }
            polySegs[i].tx <<= 16;
            polySegs[i].fx <<= 16;

            // make sure "from" is above "to"
            if (polySegs[i].fy > polySegs[i].ty) {
                j = polySegs[i].tx;
                polySegs[i].tx = polySegs[i].fx;
                polySegs[i].fx = j;
                j = polySegs[i].ty;
                polySegs[i].ty = polySegs[i].fy;
                polySegs[i].fy = j;
            }

            // insert this edge into the edgelist, sorted by ascending "fy"
            if (edgelist == null) {
                edgelist = polySegs[i];
                polySegs[i].nextedge = null;
            } else {
                // insert by ascending "fy"
                if (edgelist.fy > polySegs[i].fy) {
                    polySegs[i].nextedge = edgelist;
                    edgelist = polySegs[i];
                } else {
                    for (PolySeg a = edgelist; a != null; a = a.nextedge) {
                        if (a.nextedge == null
                                || a.nextedge.fy > polySegs[i].fy) {
                            // insert after this
                            polySegs[i].nextedge = a.nextedge;
                            a.nextedge = polySegs[i];
                            break;
                        }
                    }
                }
            }
        }

        // scan polygon and render
        int ycur = 0;
        PolySeg active = null;
        while (active != null || edgelist != null) {
            if (active == null) {
                active = edgelist;
                active.nextactive = null;
                edgelist = edgelist.nextedge;
                ycur = active.fy;
            }

            // introduce edges from edge list into active list
            while (edgelist != null && edgelist.fy <= ycur) {
                // insert "edgelist" into active list, sorted by "fx" coordinate
                if (active.fx > edgelist.fx
                        || (active.fx == edgelist.fx && active.increment > edgelist.increment)) {
                    edgelist.nextactive = active;
                    active = edgelist;
                    edgelist = edgelist.nextedge;
                } else {
                    for (PolySeg a = active; a != null; a = a.nextactive) {
                        if (a.nextactive == null
                                || a.nextactive.fx > edgelist.fx
                                || (a.nextactive.fx == edgelist.fx
                                && a.nextactive.increment > edgelist.increment)) {
                            // insert after this
                            edgelist.nextactive = a.nextactive;
                            a.nextactive = edgelist;
                            edgelist = edgelist.nextedge;
                            break;
                        }
                    }
                }
            }

            // generate regions to be filled in on current scan line
            int wrap = 0;
            PolySeg left = active;
            for (PolySeg edge = active; edge != null; edge = edge.nextactive) {
                wrap = wrap + edge.direction;
                if (wrap == 0) {
                    int j = (left.fx + 32768) >> 16;
                    int k = (edge.fx + 32768) >> 16;

                    raster.fillHorLine(ycur, j, k);

                    left = edge.nextactive;
                }
            }
            ycur++;

            // update edges in active list
            PolySeg lastedge = null;
            for (PolySeg edge = active; edge != null; edge = edge.nextactive) {
                if (ycur >= edge.ty) {
                    if (lastedge == null) {
                        active = edge.nextactive;
                    } else {
                        lastedge.nextactive = edge.nextactive;
                    }
                } else {
                    edge.fx += edge.increment;
                    lastedge = edge;
                }
            }
        }

        // if outlined pattern, draw the outline
        EGraphics.Outline o = raster.getOutline();
        if (o == null) {
            return;
        }
        for (int i = 0; i < points.length; i++) {
            int last = i - 1;
            if (last < 0) {
                last = points.length - 1;
            }
            int fX = points[last].x;
            int fY = points[last].y;
            int tX = points[i].x;
            int tY = points[i].y;
            drawOutline(fX, fY, tX, tY, o.getPattern(), o.getLen(), raster);
            if (o.getThickness() != 1) {
                int ang = GenMath.figureAngle(new Point2D.Double(fX, fY), new Point2D.Double(tX, tY));
                double sin = DBMath.sin(ang + 900);
                double cos = DBMath.cos(ang + 900);
                for (int t = 1; t < o.getThickness(); t++) {
                    int dX = (int) (cos * t + 0.5);
                    int dY = (int) (sin * t + 0.5);
                    drawOutline(fX + dX, fY + dY, tX + dX, tY + dY, o.getPattern(), o.getLen(), raster);
                }
            }
        }
    }

    // ************************************* CIRCLE DRAWING *************************************
    /**
     * Method to draw a filled-in circle of radius "radius" on the off-screen buffer
     */
    void drawCircle(long gridCenterX, long gridCenterY, long gridEdgeX, long gridEdgeY, ERaster raster) {
        gridToScreen(gridCenterX, gridCenterY, tempPt1);
        gridToScreen(gridEdgeX, gridEdgeY, tempPt2);
        drawCircle(tempPt1, tempPt2, raster);
    }

    /**
     * Method to draw a circle on the off-screen buffer
     */
    private void drawCircle(Point center, Point edge, ERaster raster) {
        // get parameters
        int radius = (int) center.distance(edge);

        // set redraw area
        int left = center.x - radius;
        int right = center.x + radius + 1;
        int top = center.y - radius;
        int bottom = center.y + radius + 1;

        int x = 0;
        int y = radius;
        int d = 3 - 2 * radius;
        if (left >= 0 && right < sz.width && top >= 0 && bottom < sz.height) {
            // no clip version is faster
            while (x <= y) {
                raster.drawPoint(center.x + x, center.y + y);
                raster.drawPoint(center.x - x, center.y + y);
                raster.drawPoint(center.x + x, center.y - y);
                raster.drawPoint(center.x - x, center.y - y);
                raster.drawPoint(center.x + y, center.y + x);
                raster.drawPoint(center.x - y, center.y + x);
                raster.drawPoint(center.x + y, center.y - x);
                raster.drawPoint(center.x - y, center.y - x);

                if (d < 0) {
                    d += 4 * x + 6;
                } else {
                    d += 4 * (x - y) + 10;
                    y--;
                }
                x++;
            }
        } else {
            // clip version
            while (x <= y) {
                int thisy = center.y + y;
                if (thisy >= 0 && thisy < sz.height) {
                    int thisx = center.x + x;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                    thisx = center.x - x;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                }

                thisy = center.y - y;
                if (thisy >= 0 && thisy < sz.height) {
                    int thisx = center.x + x;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                    thisx = center.x - x;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                }

                thisy = center.y + x;
                if (thisy >= 0 && thisy < sz.height) {
                    int thisx = center.x + y;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                    thisx = center.x - y;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                }

                thisy = center.y - x;
                if (thisy >= 0 && thisy < sz.height) {
                    int thisx = center.x + y;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                    thisx = center.x - y;
                    if (thisx >= 0 && thisx < sz.width) {
                        raster.drawPoint(thisx, thisy);
                    }
                }

                if (d < 0) {
                    d += 4 * x + 6;
                } else {
                    d += 4 * (x - y) + 10;
                    y--;
                }
                x++;
            }
        }
    }

    /**
     * Method to draw a filled-in circle of radius "radius" on the off-screen buffer
     */
    void drawThickCircle(long gridCenterX, long gridCenterY, long gridEdgeX, long gridEdgeY, ERaster raster) {
        gridToScreen(gridCenterX, gridCenterY, tempPt1);
        gridToScreen(gridEdgeX, gridEdgeY, tempPt2);
        drawThickCircle(tempPt1, tempPt2, raster);
    }

    /**
     * Method to draw a thick circle on the off-screen buffer
     */
    private void drawThickCircle(Point center, Point edge, ERaster raster) {
        // get parameters
        int radius = (int) center.distance(edge);

        int x = 0;
        int y = radius;
        int d = 3 - 2 * radius;
        while (x <= y) {
            int thisy = center.y + y;
            if (thisy >= 0 && thisy < sz.height) {
                int thisx = center.x + x;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
                thisx = center.x - x;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
            }

            thisy = center.y - y;
            if (thisy >= 0 && thisy < sz.height) {
                int thisx = center.x + x;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
                thisx = center.x - x;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
            }

            thisy = center.y + x;
            if (thisy >= 0 && thisy < sz.height) {
                int thisx = center.x + y;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
                thisx = center.x - y;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
            }

            thisy = center.y - x;
            if (thisy >= 0 && thisy < sz.height) {
                int thisx = center.x + y;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
                thisx = center.x - y;
                if (thisx >= 0 && thisx < sz.width) {
                    drawThickPoint(thisx, thisy, raster);
                }
            }

            if (d < 0) {
                d += 4 * x + 6;
            } else {
                d += 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
    }

    // ************************************* DISC DRAWING *************************************
    /**
     * Method to draw a scan line of the filled-in circle of radius "radius"
     */
    private void drawDiscRow(int thisy, int startx, int endx, ERaster raster) {
        if (thisy < clipLY || thisy > clipHY) {
            return;
        }
        if (startx < clipLX) {
            startx = clipLX;
        }
        if (endx > clipHX) {
            endx = clipHX;
        }
        if (startx > endx) {
            return;
        }
        raster.fillHorLine(thisy, startx, endx);
    }

    /**
     * Method to draw a filled-in circle of radius "radius" on the off-screen buffer
     */
    void drawDisc(long gridCenterX, long gridCenterY, long gridEdgeX, long gridEdgeY, ERaster raster) {
        gridToScreen(gridCenterX, gridCenterY, tempPt1);
        gridToScreen(gridEdgeX, gridEdgeY, tempPt2);
        drawDisc(tempPt1, tempPt2, raster);
    }

    public void drawOval(int gridCenterX, int gridCenterY, int pixelRadius, ERaster raster) {
        gridToScreen(gridCenterX, gridCenterY, tempPt1);
        drawDisc(tempPt1, pixelRadius, raster);
    }

    /**
     * Method to draw a filled-in circle of radius "radius" on the off-screen buffer
     */
    private void drawDisc(Point center, Point edge, ERaster raster) {
        // get parameters
        int radius = (int) center.distance(edge);
        EGraphics.Outline o = raster.getOutline();
        if (o != null) {
            drawCircle(center, edge, raster);
        }
        drawDisc(center, radius, raster);
    }

    private void drawDisc(Point center, int radius, ERaster raster) {

        // set redraw area
        int left = center.x - radius;
        int right = center.x + radius + 1;
        int top = center.y - radius;
        int bottom = center.y + radius + 1;

        if (radius == 1) {
            // just fill the area for discs this small
            if (left < 0) {
                left = 0;
            }
            if (right >= sz.width) {
                right = sz.width - 1;
            }
            for (int y = top; y < bottom; y++) {
                if (y < 0 || y >= sz.height) {
                    continue;
                }
                for (int x = left; x < right; x++) {
                    raster.drawPoint(x, y);
                }
            }
            return;
        }

        int x = 0;
        int y = radius;
        int d = 3 - 2 * radius;
        while (x <= y) {
            drawDiscRow(center.y + y, center.x - x, center.x + x, raster);
            drawDiscRow(center.y - y, center.x - x, center.x + x, raster);
            drawDiscRow(center.y + x, center.x - y, center.x + y, raster);
            drawDiscRow(center.y - x, center.x - y, center.x + y, raster);

            if (d < 0) {
                d += 4 * x + 6;
            } else {
                d += 4 * (x - y) + 10;
                y--;
            }
            x++;
        }
    }
    // ************************************* ARC DRAWING *************************************
    private boolean[] arcOctTable = new boolean[9];
    private Point arcCenter;
    private int arcRadius;
    private ERaster arcRaster;
    private boolean arcThick;

    private int arcFindOctant(int x, int y) {
        if (x > 0) {
            if (y >= 0) {
                if (y >= x) {
                    return 7;
                }
                return 8;
            }
            if (x >= -y) {
                return 1;
            }
            return 2;
        }
        if (y > 0) {
            if (y > -x) {
                return 6;
            }
            return 5;
        }
        if (y > x) {
            return 4;
        }
        return 3;
    }

    private Point arcXformOctant(int x, int y, int oct) {
        switch (oct) {
            case 1:
                return new Point(-y, x);
            case 2:
                return new Point(x, -y);
            case 3:
                return new Point(-x, -y);
            case 4:
                return new Point(-y, -x);
            case 5:
                return new Point(y, -x);
            case 6:
                return new Point(-x, y);
            case 7:
                return new Point(x, y);
            case 8:
                return new Point(y, x);
        }
        return null;
    }

    private void arcDoPixel(int x, int y) {
        if (x < clipLX || x > clipHX || y < clipLY || y > clipHY) {
            return;
        }
        if (arcThick) {
            drawThickPoint(x, y, arcRaster);
        } else {
            arcRaster.drawPoint(x, y);
        }
    }

    private void arcOutXform(int x, int y) {
        if (arcOctTable[1]) {
            arcDoPixel(y + arcCenter.x, -x + arcCenter.y);
        }
        if (arcOctTable[2]) {
            arcDoPixel(x + arcCenter.x, -y + arcCenter.y);
        }
        if (arcOctTable[3]) {
            arcDoPixel(-x + arcCenter.x, -y + arcCenter.y);
        }
        if (arcOctTable[4]) {
            arcDoPixel(-y + arcCenter.x, -x + arcCenter.y);
        }
        if (arcOctTable[5]) {
            arcDoPixel(-y + arcCenter.x, x + arcCenter.y);
        }
        if (arcOctTable[6]) {
            arcDoPixel(-x + arcCenter.x, y + arcCenter.y);
        }
        if (arcOctTable[7]) {
            arcDoPixel(x + arcCenter.x, y + arcCenter.y);
        }
        if (arcOctTable[8]) {
            arcDoPixel(y + arcCenter.x, x + arcCenter.y);
        }
    }

    private void arcBresCW(Point pt, Point pt1) {
        int d = 3 - 2 * pt.y + 4 * pt.x;
        while (pt.x < pt1.x && pt.y > pt1.y) {
            arcOutXform(pt.x, pt.y);
            if (d < 0) {
                d += 4 * pt.x + 6;
            } else {
                d += 4 * (pt.x - pt.y) + 10;
                pt.y--;
            }
            pt.x++;
        }

        // get to the end
        for (; pt.x < pt1.x; pt.x++) {
            arcOutXform(pt.x, pt.y);
        }
        for (; pt.y > pt1.y; pt.y--) {
            arcOutXform(pt.x, pt.y);
        }
        arcOutXform(pt1.x, pt1.y);
    }

    private void arcBresMidCW(Point pt) {
        int d = 3 - 2 * pt.y + 4 * pt.x;
        while (pt.x < pt.y) {
            arcOutXform(pt.x, pt.y);
            if (d < 0) {
                d += 4 * pt.x + 6;
            } else {
                d += 4 * (pt.x - pt.y) + 10;
                pt.y--;
            }
            pt.x++;
        }
        if (pt.x == pt.y) {
            arcOutXform(pt.x, pt.y);
        }
    }

    private void arcBresMidCCW(Point pt) {
        int d = 3 + 2 * pt.y - 4 * pt.x;
        while (pt.x > 0) {
            arcOutXform(pt.x, pt.y);
            if (d > 0) {
                d += 6 - 4 * pt.x;
            } else {
                d += 4 * (pt.y - pt.x) + 10;
                pt.y++;
            }
            pt.x--;
        }
        arcOutXform(0, arcRadius);
    }

    private void arcBresCCW(Point pt, Point pt1) {
        int d = 3 + 2 * pt.y + 4 * pt.x;
        while (pt.x > pt1.x && pt.y < pt1.y) {
            // not always correct
            arcOutXform(pt.x, pt.y);
            if (d > 0) {
                d += 6 - 4 * pt.x;
            } else {
                d += 4 * (pt.y - pt.x) + 10;
                pt.y++;
            }
            pt.x--;
        }

        // get to the end
        for (; pt.x > pt1.x; pt.x--) {
            arcOutXform(pt.x, pt.y);
        }
        for (; pt.y < pt1.y; pt.y++) {
            arcOutXform(pt.x, pt.y);
        }
        arcOutXform(pt1.x, pt1.y);
    }

    /**
     * draws an arc centered at (centerx, centery), clockwise,
     * passing by (x1,y1) and (x2,y2)
     */
    void drawCircleArc(long gridCenterX, long gridCenterY, long gridX1, long gridY1, long gridX2, long gridY2, boolean thick, ERaster raster) {
        gridToScreen(gridCenterX, gridCenterY, tempPt1);
        gridToScreen(gridX1, gridY1, tempPt2);
        gridToScreen(gridX2, gridY2, tempPt3);
        drawCircleArc(tempPt1, tempPt2, tempPt3, thick, raster);
    }

    /**
     * draws an arc centered at (centerx, centery), clockwise,
     * passing by (x1,y1) and (x2,y2)
     */
    private void drawCircleArc(Point center, Point p1, Point p2, boolean thick, ERaster raster) {
        // ignore tiny arcs
        if (p1.x == p2.x && p1.y == p2.y) {
            return;
        }

        // get parameters
        arcRaster = raster;

        arcCenter = center;
        int pa_x = p2.x - arcCenter.x;
        int pa_y = p2.y - arcCenter.y;
        int pb_x = p1.x - arcCenter.x;
        int pb_y = p1.y - arcCenter.y;
        arcRadius = (int) arcCenter.distance(p2);
        int alternate = (int) arcCenter.distance(p1);
        int start_oct = arcFindOctant(pa_x, pa_y);
        int end_oct = arcFindOctant(pb_x, pb_y);
        arcThick = thick;

        // move the point
        if (arcRadius != alternate) {
            int diff = arcRadius - alternate;
            switch (end_oct) {
                case 6:
                case 7: /*  y >  x */ pb_y += diff;
                    break;
                case 8: /*  x >  y */
                case 1: /*  x > -y */ pb_x += diff;
                    break;
                case 2: /* -y >  x */
                case 3: /* -y > -x */ pb_y -= diff;
                    break;
                case 4: /* -y < -x */
                case 5: /*  y < -x */ pb_x -= diff;
                    break;
            }
        }

        for (int i = 1; i < 9; i++) {
            arcOctTable[i] = false;
        }

        if (start_oct == end_oct) {
            arcOctTable[start_oct] = true;
            Point pa = arcXformOctant(pa_x, pa_y, start_oct);
            Point pb = arcXformOctant(pb_x, pb_y, start_oct);

            if ((start_oct & 1) != 0) {
                arcBresCW(pa, pb);
            } else {
                arcBresCCW(pa, pb);
            }
            arcOctTable[start_oct] = false;
        } else {
            arcOctTable[start_oct] = true;
            Point pt = arcXformOctant(pa_x, pa_y, start_oct);
            if ((start_oct & 1) != 0) {
                arcBresMidCW(pt);
            } else {
                arcBresMidCCW(pt);
            }
            arcOctTable[start_oct] = false;

            arcOctTable[end_oct] = true;
            pt = arcXformOctant(pb_x, pb_y, end_oct);
            if ((end_oct & 1) != 0) {
                arcBresMidCCW(pt);
            } else {
                arcBresMidCW(pt);
            }
            arcOctTable[end_oct] = false;

            if (MODP(start_oct + 1) != end_oct) {
                if (MODP(start_oct + 1) == MODM(end_oct - 1)) {
                    arcOctTable[MODP(start_oct + 1)] = true;
                } else {
                    for (int i = MODP(start_oct + 1); i != end_oct; i = MODP(i + 1)) {
                        arcOctTable[i] = true;
                    }
                }
                arcBresMidCW(new Point(0, arcRadius));
            }
        }
    }

    private int MODM(int x) {
        return (x < 1) ? x + 8 : x;
    }

    private int MODP(int x) {
        return (x > 8) ? x - 8 : x;
    }

    // ************************************* RENDERING SUPPORT *************************************
    void drawThickPoint(int x, int y, ERaster raster) {
        raster.drawPoint(x, y);
        if (x > clipLX) {
            raster.drawPoint(x - 1, y);
        }
        if (x < clipHX) {
            raster.drawPoint(x + 1, y);
        }
        if (y > clipLY) {
            raster.drawPoint(x, y - 1);
        }
        if (y < sz.height - 1) {
            raster.drawPoint(x, y + 1);
        }
    }

    /**
     * Method to convert a database coordinate to screen coordinates.
     * @param dbX the X coordinate (in database units).
     * @param dbY the Y coordinate (in database units).
     * @param result the Point in which to store the screen coordinates.
     */
    public void databaseToScreen(double dbX, double dbY, Point result) {
        double scrX = originX + dbX * scale;
        double scrY = originY - dbY * scale;
        result.x = (int) (scrX >= 0 ? scrX + 0.5 : scrX - 0.5);
        result.y = (int) (scrY >= 0 ? scrY + 0.5 : scrY - 0.5);
    }

    void gridToScreen(long dbX, long dbY, Point result) {
        double scrX = (dbX - factorX) * scale_;
        double scrY = (factorY - dbY) * scale_;
        result.x = (int) (scrX >= 0 ? scrX + 0.5 : scrX - 0.5);
        result.y = (int) (scrY >= 0 ? scrY + 0.5 : scrY - 0.5);
    }

    void screenToGrid(int scrX, int scrY, Point result) {
        double dbX = scrX / scale_ + factorX;
        double dbY = factorY - scrY / scale_;
        result.x = (int) (dbX >= 0 ? dbX + 0.5 : dbX - 0.5);
        result.y = (int) (dbY >= 0 ? dbY + 0.5 : dbY - 0.5);
    }

    void screenToDatabase(int x, int y, Point2D result) {
        result.setLocation((x - originX) / scale, (originY - y) / scale);
    }

    /**
     * Method to convert a database rectangle to screen coordinates.
     * @param db the rectangle (in database units).
     * @return the rectangle on the screen.
     */
    Rectangle databaseToScreen(Rectangle2D db) {
        Point llPt = tempPt1;
        Point urPt = tempPt2;
        databaseToScreen(db.getMinX(), db.getMinY(), llPt);
        databaseToScreen(db.getMaxX(), db.getMaxY(), urPt);
        int screenLX = llPt.x;
        int screenHX = urPt.x;
        int screenLY = llPt.y;
        int screenHY = urPt.y;
        if (screenHX < screenLX) {
            int swap = screenHX;
            screenHX = screenLX;
            screenLX = swap;
        }
        if (screenHY < screenLY) {
            int swap = screenHY;
            screenHY = screenLY;
            screenLY = swap;
        }
        return new Rectangle(screenLX, screenLY, screenHX - screenLX + 1, screenHY - screenLY + 1);
    }
}
