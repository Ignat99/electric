/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FixpTransform.java
 * Written by: Dmitry Nadezhin.
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.util.math;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * The subclass of AffineTransform. It represent a flip/rotation specified by
 * Electric {@link com.sun.electric.util.math.Orientation} class, followed by a
 * translation specified by a pair of {@link com.sun.electric.util.math.ECoord }
 * coordinates.
 */
public class FixpTransform extends AffineTransform {

    private long fixpX;
    private long fixpY;
    private Orientation orient;

    /**
     * @inheritDoc
     */
    public FixpTransform() {
        orient = Orientation.IDENT;
    }

    /**
     * @inheritDoc
     */
    public FixpTransform(AffineTransform Tx) {
        super(Tx);
        if (Tx instanceof FixpTransform && ((FixpTransform) Tx).orient != null) {
            FixpTransform ft = (FixpTransform) Tx;
            this.fixpX = ft.fixpX;
            this.fixpY = ft.fixpY;
            this.orient = ft.orient;
        }
    }

    /**
     * @inheritDoc
     */
    public FixpTransform(float m00, float m10, float m01, float m11, float m02, float m12) {
        super(m00, m10, m01, m11, m02, m12);
    }

    /**
     * @inheritDoc
     */
    public FixpTransform(float[] flatmatrix) {
        super(flatmatrix);
    }

    /**
     * @inheritDoc
     */
    public FixpTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        super(m00, m10, m01, m11, m02, m12);
    }

    /**
     * @inheritDoc
     */
    public FixpTransform(double[] flatmatrix) {
        super(flatmatrix);
    }

    /**
     * Constructs FixpTransform that is the same as specified FixpTransform.
     * @param Tx specified transform
     */
    public FixpTransform(FixpTransform Tx) {
        setTransform(Tx.fixpX, Tx.fixpY, Tx.orient);
    }

    /**
     * Constructs FixpTransform that flip/rotation specified by
     * <code>orient</code> followed by translation specified by fixed-points
     * coordinate.
     *
     * @param fixpX fixed-point X coordinate
     * @param fixpY fixed-point Y coordinate
     * @param orient flip/rotation
     */
    public FixpTransform(long fixpX, long fixpY, Orientation orient) {
        super.setTransform(orient.m00, orient.m10, orient.m01, orient.m11,
                FixpCoord.fixpToLambda(fixpX), FixpCoord.fixpToLambda(fixpY));
        this.fixpX = fixpX;
        this.fixpY = fixpY;
        this.orient = orient;
    }

    public FixpTransform(AbstractFixpPoint anchor, Orientation orient) {
        this(anchor.getFixpX(), anchor.getFixpY(), orient);
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getTranslateInstance(double tx, double ty) {
        FixpTransform ft = new FixpTransform();
        ft.setToTranslation(tx, ty);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getRotateInstance(double theta) {
        FixpTransform ft = new FixpTransform();
        ft.setToRotation(theta);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getRotateInstance(double theta, double anchorx, double anchory) {
        FixpTransform ft = new FixpTransform();
        ft.setToRotation(theta, anchorx, anchory);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getRotateInstance(double vecx, double vecy) {
        FixpTransform ft = new FixpTransform();
        ft.setToRotation(vecx, vecy);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getRotateInstance(double vecx, double vecy, double anchorx, double anchory) {
        FixpTransform ft = new FixpTransform();
        ft.setToRotation(vecx, vecy, anchorx, anchory);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getQuadrantRotateInstance(int numquadrants) {
        FixpTransform ft = new FixpTransform();
        ft.setToQuadrantRotation(numquadrants);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getQuadrantRotateInstance(int numquadrants, double anchorx, double anchory) {
        FixpTransform ft = new FixpTransform();
        ft.setToQuadrantRotation(numquadrants, anchorx, anchory);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getScaleInstance(double sx, double sy) {
        FixpTransform ft = new FixpTransform();
        ft.setToScale(sx, sy);
        return ft;
    }

    /**
     * @inheritDoc
     */
    public static FixpTransform getShearInstance(double sx, double sy) {
        FixpTransform ft = new FixpTransform();
        ft.setToShear(sx, sy);
        return ft;
    }

    /**
     * @inheritDoc
     */
    @Override
    public int getType() {
        if (orient != null) {
            int type = orient.getType();
            if ((fixpX | fixpY) != 0) {
                type |= TYPE_TRANSLATION;
            }
            return type;
        } else {
            return super.getType();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public double getDeterminant() {
        return orient != null ? orient.getDeterminant() : super.getDeterminant();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void translate(double tx, double ty) {
        if (orient != null) {
            long fixpTx = FixpCoord.lambdaToFixp(tx);
            long fixpTy = FixpCoord.lambdaToFixp(ty);
            setTransform(fixpX + orient.transformX(fixpTx, fixpTy), fixpY + orient.transformY(fixpTx, fixpTy), orient);
        } else {
            super.translate(tx, ty);
            orient = null;
        }
    }

    public void translateFixp(long fixpx, long fixpy) {
        if (orient != null) {
            setTransform(fixpX + orient.transformX(fixpx, fixpy), fixpY + orient.transformY(fixpx, fixpy), orient);
        } else {
            super.translate(FixpCoord.fixpToLambda(fixpx), FixpCoord.fixpToLambda(fixpy));
            orient = null;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void rotate(double theta) {
        super.rotate(theta);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void rotate(double theta, double anchorx, double anchory) {
        super.rotate(theta, anchorx, anchory);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void rotate(double vecx, double vecy) {
        super.rotate(vecx, vecy);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void rotate(double vecx, double vecy, double anchorx, double anchory) {
        super.rotate(vecx, vecy, anchorx, anchory);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void quadrantRotate(int numquadrants) {
        if (orient != null) {
            setTransform(fixpX, fixpY, orient.concatenate(Orientation.fromQuadrants(numquadrants)));
        } else {
            super.quadrantRotate(numquadrants);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void quadrantRotate(int numquadrants, double anchorx, double anchory) {
        if (orient != null) {
            Orientation rotate = Orientation.fromQuadrants(numquadrants);
            long fixpAnchorX = FixpCoord.lambdaToFixp(anchorx);
            long fixpAnchorY = FixpCoord.lambdaToFixp(anchory);
            long newFixpX = fixpX + fixpAnchorX + rotate.transformX(-fixpAnchorX, -fixpAnchorY);
            long newFixpY = fixpY + fixpAnchorY + rotate.transformY(-fixpAnchorX, -fixpAnchorY);
            setTransform(newFixpX, newFixpY, orient.concatenate(rotate));
        } else {
            super.quadrantRotate(numquadrants, anchorx, anchory);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void scale(double sx, double sy) {
        super.scale(sx, sy);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void shear(double shx, double shy) {
        super.shear(shx, shy);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToIdentity() {
        setTransform(0, 0, Orientation.IDENT);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToTranslation(double tx, double ty) {
        setTransform(FixpCoord.lambdaToFixp(tx), FixpCoord.lambdaToFixp(ty), Orientation.IDENT);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToRotation(double theta) {
        super.setToRotation(theta);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToRotation(double theta, double anchorx, double anchory) {
        super.setToRotation(theta, anchorx, anchory);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToRotation(double vecx, double vecy) {
        super.setToRotation(vecx, vecy);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToRotation(double vecx, double vecy, double anchorx, double anchory) {
        super.setToRotation(vecx, vecy, anchorx, anchory);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToQuadrantRotation(int numquadrants) {
        setTransform(0, 0, Orientation.fromQuadrants(numquadrants));
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToQuadrantRotation(int numquadrants, double anchorx, double anchory) {
        Orientation rotate = Orientation.fromQuadrants(numquadrants);
        long fixpAnchorX = FixpCoord.lambdaToFixp(anchorx);
        long fixpAnchorY = FixpCoord.lambdaToFixp(anchory);
        long newFixpX = fixpAnchorX + rotate.transformX(-fixpAnchorX, -fixpAnchorY);
        long newFixpY = fixpAnchorY + rotate.transformY(-fixpAnchorX, -fixpAnchorY);
        setTransform(newFixpX, newFixpY, rotate);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToScale(double sx, double sy) {
        super.setToScale(sx, sy);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setToShear(double shx, double shy) {
        super.setToShear(shx, shy);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setTransform(AffineTransform Tx) {
        super.setTransform(Tx);
        orient = null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setTransform(double m00, double m10,
            double m01, double m11,
            double m02, double m12) {
        super.setTransform(m00, m10, m01, m11, m02, m12);
        orient = null;
    }

    /**
     * Set this transform to the flip/rotation defined by
     * <code>orient</code> followed by translation
     *
     * @param orient flip/rotation
     * @param fixpX X-coordinate of translation
     * @param fixpY Y-coordinate of translation
     */
    public void setTransform(long fixpX, long fixpY, Orientation orient) {
        super.setTransform(orient.m00, orient.m10, orient.m01, orient.m11,
                FixpCoord.fixpToLambda(fixpX), FixpCoord.fixpToLambda(fixpY));
        this.fixpX = fixpX;
        this.fixpY = fixpY;
        this.orient = orient;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void concatenate(AffineTransform Tx) {
        if (orient != null && Tx instanceof FixpTransform && ((FixpTransform) Tx).orient != null) {
            FixpTransform ft = (FixpTransform) Tx;
            long fixpTx = ft.fixpX;
            long fixpTy = ft.fixpY;
            Orientation orientTx = ft.orient;
            long newFixpX = fixpX + orient.transformX(fixpTx, fixpTy);
            long newFixpY = fixpY + orient.transformY(fixpTx, fixpTy);
            setTransform(newFixpX, newFixpY, orient.concatenate(orientTx));
        } else {
            super.concatenate(Tx);
            orient = null;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void preConcatenate(AffineTransform Tx) {
        if (orient != null && Tx instanceof FixpTransform && ((FixpTransform) Tx).orient != null) {
            FixpTransform ft = (FixpTransform) Tx;
            long fixpTx = ft.fixpX;
            long fixpTy = ft.fixpY;
            Orientation orientTx = ft.orient;
            long newFixpX = fixpTx + orientTx.transformX(fixpX, fixpY);
            long newFixpY = fixpTy + orientTx.transformY(fixpX, fixpY);
            setTransform(newFixpX, newFixpY, orientTx.concatenate(orient));
        } else {
            super.preConcatenate(Tx);
            orient = null;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public FixpTransform createInverse() throws NoninvertibleTransformException {
        if (orient != null) {
            return new FixpTransform(-orient.transformX(fixpX, fixpY), -orient.transformY(fixpX, fixpY), orient.inverse());
        } else {
            FixpTransform ft = new FixpTransform(this);
            ft.invert();
            return ft;
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void invert() throws NoninvertibleTransformException {
        if (orient != null) {
            setTransform(-orient.transformX(fixpX, fixpY), -orient.transformY(fixpX, fixpY), orient.inverse());
        } else {
            super.invert();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Point2D transform(Point2D ptSrc, Point2D ptDst) {
        return orient != null ? orient.transform(fixpX, fixpY, ptSrc, ptDst) : super.transform(ptSrc, ptDst);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void transform(Point2D[] ptSrc, int srcOff,
            Point2D[] ptDst, int dstOff,
            int numPts) {
        if (orient != null && ptSrc instanceof AbstractFixpPoint[] && ptDst instanceof AbstractFixpPoint[]) {
            orient.transform(fixpX, fixpY, (AbstractFixpPoint[]) ptSrc, srcOff, (AbstractFixpPoint[]) ptDst, dstOff, numPts);
        } else {
            while (--numPts >= 0) {
                // Copy source coords into local variables in case src == dst
                Point2D src = ptSrc[srcOff++];
                Point2D dst = ptDst[dstOff++];
                if (dst == null) {
                    ptDst[dstOff - 1] = transform(src, null);
                } else {
                    transform(src, dst);
                }
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void transform(float[] srcPts, int srcOff,
            float[] dstPts, int dstOff,
            int numPts) {
        super.transform(srcPts, srcOff, dstPts, dstOff, numPts);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void transform(double[] srcPts, int srcOff,
            double[] dstPts, int dstOff,
            int numPts) {
        super.transform(srcPts, srcOff, dstPts, dstOff, numPts);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void transform(float[] srcPts, int srcOff,
            double[] dstPts, int dstOff,
            int numPts) {
        super.transform(srcPts, srcOff, dstPts, dstOff, numPts);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void transform(double[] srcPts, int srcOff,
            float[] dstPts, int dstOff,
            int numPts) {
        super.transform(srcPts, srcOff, dstPts, dstOff, numPts);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Point2D inverseTransform(Point2D ptSrc, Point2D ptDst) {
        if (ptSrc instanceof AbstractFixpPoint) {
            AbstractFixpPoint fpSrc = (AbstractFixpPoint) ptSrc;
            long srcX = fpSrc.getFixpX() - fixpX;
            long srcY = fpSrc.getFixpY() - fixpY;
            Orientation inverse = orient.inverse();
            long dstX = inverse.transformX(srcX, srcY);
            long dstY = inverse.transformY(srcX, srcY);
            if (ptDst == null) {
                ptDst = fpSrc.create(dstX, dstY);
            } else if (ptDst instanceof AbstractFixpPoint) {
                ((AbstractFixpPoint) ptDst).setFixpLocation(dstX, dstY);
            } else {
                ptDst.setLocation(FixpCoord.fixpToLambda(dstX), FixpCoord.fixpToLambda(dstY));
            }
            return ptDst;
        }
        double srcX = ptSrc.getX() - FixpCoord.fixpToLambda(fixpX);
        double srcY = ptSrc.getY() - FixpCoord.fixpToLambda(fixpY);
        double dstX = orient.transformX(srcX, srcY);
        double dstY = orient.transformY(srcX, srcY);
        if (ptDst == null) {
            if (ptSrc instanceof Point2D.Double) {
                ptDst = new Point2D.Double();
            } else {
                ptDst = new Point2D.Float();
            }
        }
        ptDst.setLocation(dstX, dstY);
        return ptDst;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void inverseTransform(double[] srcPts, int srcOff,
            double[] dstPts, int dstOff,
            int numPts) {
        try {
            super.inverseTransform(srcPts, srcOff, dstPts, dstOff, numPts);
        } catch (NoninvertibleTransformException e) {
            throw new AssertionError();
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Point2D deltaTransform(Point2D ptSrc, Point2D ptDst) {
        return orient != null ? orient.transform(0, 0, ptSrc, ptDst) : super.deltaTransform(ptSrc, ptDst);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void deltaTransform(double[] srcPts, int srcOff,
            double[] dstPts, int dstOff,
            int numPts) {
        super.deltaTransform(srcPts, srcOff, dstPts, dstOff, numPts);
    }

    public void transform(AbstractFixpRectangle rectSrc, AbstractFixpRectangle rectDst) {
        if (orient != null) {
            orient.rectangleBounds(rectSrc, fixpX, fixpY, rectDst);
        } else {
            double[] coords = new double[]{rectSrc.getMinX(), rectSrc.getMinY(), rectSrc.getMaxX(), rectSrc.getMaxY()};
            transform(coords, 0, coords, 0, 2);
            rectDst.setFrameFromDiagonal(coords[0], coords[1], coords[2], coords[3]);
        }
    }

    public void transform(Rectangle2D rectSrc, Rectangle2D rectDst) {
        if (rectSrc instanceof AbstractFixpRectangle && rectDst instanceof AbstractFixpRectangle) {
            transform((AbstractFixpRectangle)rectSrc, (AbstractFixpRectangle)rectDst);
        } else if (orient != null) {
            orient.rectangleBounds(rectSrc.getMinX(), rectSrc.getMinY(), rectSrc.getMaxX(), rectSrc.getMaxY(),
                    ECoord.fixpToLambda(fixpX), ECoord.fixpToLambda(fixpY), rectDst);
        } else {
            double[] coords = new double[]{rectSrc.getMinX(), rectSrc.getMinY(), rectSrc.getMaxX(), rectSrc.getMaxY()};
            transform(coords, 0, coords, 0, 2);
            rectDst.setFrameFromDiagonal(coords[0], coords[1], coords[2], coords[3]);
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public Shape createTransformedShape(Shape pSrc) {
        return super.createTransformedShape(pSrc);
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isIdentity() {
        return orient != null ? orient.isIdent() && (fixpX | fixpY) == 0 : super.isIdentity();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FixpTransform) {
            FixpTransform that = (FixpTransform) obj;
            if (orient != null && that.orient != null) {
                return this.orient.canonic() == that.orient.canonic() && this.fixpX == that.fixpX && this.fixpY == that.fixpY;
            }
        }
        return super.equals(obj);
    }

    private static boolean isFixp(AffineTransform Tx) {
        if (Tx instanceof FixpTransform) {
            return ((FixpTransform) Tx).orient != null;
        }
        return getOrient(Tx) != null
                && FixpCoord.fixpToLambda(getFixpX(Tx)) == Tx.getTranslateX()
                && FixpCoord.fixpToLambda(getFixpY(Tx)) == Tx.getTranslateY();
    }

    private static long getFixpX(AffineTransform Tx) {
        if (Tx instanceof FixpTransform) {
            return ((FixpTransform) Tx).fixpX;
        }
        return FixpCoord.lambdaToFixp(Tx.getTranslateX());
    }

    private static long getFixpY(AffineTransform Tx) {
        if (Tx instanceof FixpTransform) {
            return ((FixpTransform) Tx).fixpY;
        }
        return FixpCoord.lambdaToFixp(Tx.getTranslateY());
    }

    private static Orientation getOrient(AffineTransform Tx) {
        if (Tx instanceof FixpTransform) {
            return ((FixpTransform) Tx).orient;
        }
        double m00 = Tx.getScaleX();
        double m01 = Tx.getShearX();
        double m10 = Tx.getShearY();
        double m11 = Tx.getScaleY();
        Orientation or = null;
        if (Math.abs(m00) == 1.0 && Math.abs(m11) == 1.0 && m01 == 0.0 && m10 == 0.0) {
            or = m00 > 0 ? (m11 > 0 ? Orientation.IDENT : Orientation.Y) : (m11 > 0 ? Orientation.X : Orientation.RR);
        } else if (Math.abs(m01) == 1.0 && Math.abs(m10) == 1.0 && m00 == 0.0 && m11 == 0.0) {
            or = m01 > 0 ? (m10 > 0 ? Orientation.YRRR : Orientation.RRR) : (m10 > 0 ? Orientation.R : Orientation.YR);
        } else {
            return null;
        }
        assert m00 == or.m00;
        assert m01 == or.m01;
        assert m10 == or.m10;
        assert m11 == or.m11;
        return or;
    }
}
