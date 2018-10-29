/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Orientation.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.io.Serializable;

/**
 * Class
 * <code>Orientation</code> represents 2D affine transform which is composition
 * of rotation and possible flip. The C code used an angle (in tenth-degrees)
 * and a "transpose" factor which would flip the object along the major diagonal
 * after rotation. The Java code uses the same angle (in tenth-degrees) but has
 * two mirror options: Mirror X and Mirror Y.
 */
public class Orientation implements Serializable {

    // The internal representation of orientation is the 2D transformation matrix:
    // [   sX*cos(angle)   -sX*sin(angle)   ] = [ sX  0 ] * [ cos(angle) -sin(angle) ]
    // [   sY*sin(angle)    sY*cos(angle)   ]   [  0 sY ]   [ sin(angle)  cos(angle) ]
    // --------------------------------------
    // sX = jMirrorX ? -1 : 1
    // sY = jMirrorY ? -1 : 1
    // 0 <= jAngle < 3600 is in tenth-degrees
    private final short jAngle;
//	private final short jOctant;
    private final boolean jMirrorX;
    private final boolean jMirrorY;
    private final String jString;
    private final short cAngle;
    private final boolean cTranspose;
    private final Orientation inverse;
    final double m00;
    final double m01;
    final double m10;
    final double m11;
    private final FixpTransform trans;
    private final int affineTransformType;
    private static final HashMap<Integer, Orientation> map = new HashMap<Integer, Orientation>();
    private static final Orientation[] map45;
    private static final int OCTANT = 0x07;
    private static final int XMIRROR45 = 0x08;
    private static final int YMIRROR45 = 0x10;

    static {
        Orientation[] m = new Orientation[32];
        for (int i = 0; i < m.length; i++) {
            int octant = i & OCTANT;
            boolean jMirrorX = (i & XMIRROR45) != 0;
            boolean jMirrorY = (i & YMIRROR45) != 0;

            Orientation orient = new Orientation(octant * 450, jMirrorX, jMirrorY, null);
            m[i] = orient;
            if (orient.inverse == orient) {
                continue;
            }
            m[i + 8 - octant * 2] = orient.inverse;
        }
        map45 = m;
    }
    /**
     * Identical Orientation
     */
    public static final Orientation IDENT = fromJava(0, false, false);
    public static final Orientation R = fromJava(900, false, false);
    public static final Orientation RR = fromJava(1800, false, false);
    public static final Orientation RRR = fromJava(2700, false, false);
    public static final Orientation X = fromJava(0, true, false);
    public static final Orientation XR = fromJava(900, true, false);
    public static final Orientation XRR = fromJava(1800, true, false);
    public static final Orientation XRRR = fromJava(2700, true, false);
    public static final Orientation Y = fromJava(0, false, true);
    public static final Orientation YR = fromJava(900, false, true);
    public static final Orientation YRR = fromJava(1800, false, true);
    public static final Orientation YRRR = fromJava(2700, false, true);
    public static final Orientation XY = fromJava(0, true, true);
    public static final Orientation XYR = fromJava(900, true, true);
    public static final Orientation XYRR = fromJava(1800, true, true);
    public static final Orientation XYRRR = fromJava(2700, true, true);
    // flags for Manhattan orientations
    private static final byte MNONE = -1;
    private static final byte MIDENT = 0;
    private static final byte MR = 1;
    private static final byte MRR = 2;
    private static final byte MRRR = 3;
    private static final byte MY = 4;
    private static final byte MYR = 5;
    private static final byte MYRR = 6;
    private static final byte MYRRR = 7;
    private final byte manh;

    private Orientation(int jAngle, boolean jMirrorX, boolean jMirrorY, Orientation inverse) {
        assert 0 <= jAngle && jAngle < 3600;

        // store Java information
        this.jAngle = (short) jAngle;
//		this.jOctant = (short)(jAngle % 450 == 0 ? jAngle/450 : -1);
        this.jMirrorX = jMirrorX;
        this.jMirrorY = jMirrorY;

        // compute C information
        int cAngle = jAngle;
        boolean cTranspose = false;
        if (jMirrorX) {
            if (jMirrorY) {
                cAngle = (cAngle + 1800) % 3600;
            } else {
                cAngle = (cAngle + 900) % 3600;
                cTranspose = true;
            }
        } else if (jMirrorY) {
            cAngle = (cAngle + 2700) % 3600;
            cTranspose = true;
        }
        this.cAngle = (short) cAngle;
        this.cTranspose = cTranspose;
        // check for Manhattan orientation
        switch (cAngle) {
            case 0:
                if (cTranspose) {
                    manh = MYR;
                    affineTransformType = AffineTransform.TYPE_FLIP | AffineTransform.TYPE_QUADRANT_ROTATION;
                } else {
                    manh = MIDENT;
                    affineTransformType = AffineTransform.TYPE_IDENTITY;
                }
                break;
            case 900:
                if (cTranspose) {
                    manh = MYRR;
                    affineTransformType = AffineTransform.TYPE_FLIP;
                } else {
                    manh = MR;
                    affineTransformType = AffineTransform.TYPE_QUADRANT_ROTATION;
                }
                break;
            case 1800:
                if (cTranspose) {
                    manh = MYRRR;
                    affineTransformType = AffineTransform.TYPE_FLIP | AffineTransform.TYPE_QUADRANT_ROTATION;
                } else {
                    manh = MRR;
                    affineTransformType = AffineTransform.TYPE_QUADRANT_ROTATION;
                }
                break;
            case 2700:
                if (cTranspose) {
                    manh = MY;
                    affineTransformType = AffineTransform.TYPE_FLIP;
                } else {
                    manh = MRRR;
                    affineTransformType = AffineTransform.TYPE_QUADRANT_ROTATION;
                }
                break;
            default:
                manh = MNONE;
                if (cTranspose) {
                    affineTransformType = AffineTransform.TYPE_FLIP | AffineTransform.TYPE_GENERAL_ROTATION;
                } else {
                    affineTransformType = AffineTransform.TYPE_GENERAL_ROTATION;
                }
        }

        if (inverse == null) {
            if (cTranspose || jAngle == 0 || jAngle == 1800) {
                inverse = this;
            } else {
                inverse = new Orientation(3600 - jAngle, jMirrorX, jMirrorY, this);
            }
        }
        this.inverse = inverse;

        double[] matrix = new double[4];
        int sect = jAngle / 450;
        assert 0 <= sect && sect < 8;
        int ang = jAngle % 450;
        if (sect % 2 != 0) {
            ang = 450 - ang;
        }
        assert 0 <= ang && ang <= 450;
        double cos0, sin0;
        if (ang == 0) {
            cos0 = 1;
            sin0 = 0;
        } else if (ang == 450) {
            cos0 = sin0 = StrictMath.sqrt(0.5);
        } else {
            double alpha = ang * Math.PI / 1800.0;
            cos0 = StrictMath.cos(alpha);
            sin0 = StrictMath.sin(alpha);
        }
        double cos = 0, sin = 0;
        switch (sect) {
            case 0:
                cos = cos0;
                sin = sin0;
                break;
            case 1:
                cos = sin0;
                sin = cos0;
                break;
            case 2:
                cos = -sin0;
                sin = cos0;
                break;
            case 3:
                cos = -cos0;
                sin = sin0;
                break;
            case 4:
                cos = -cos0;
                sin = -sin0;
                break;
            case 5:
                cos = -sin0;
                sin = -cos0;
                break;
            case 6:
                cos = sin0;
                sin = -cos0;
                break;
            case 7:
                cos = cos0;
                sin = -sin0;
                break;
            default:
                assert false;
        }
        matrix[0] = cos * (jMirrorX ? -1 : 1);
        matrix[1] = sin * (jMirrorY ? -1 : 1);
        matrix[2] = sin * (jMirrorX ? 1 : -1);
        matrix[3] = cos * (jMirrorY ? -1 : 1);
        AffineTransform trans = new AffineTransform(matrix);
        m00 = trans.getScaleX();
        m01 = trans.getShearX();
        m10 = trans.getShearY();
        m11 = trans.getScaleY();
        this.trans = new FixpTransform(0, 0, this);

        // compute Jelib String
        String s = "";
        if (jMirrorX) {
            s += 'X';
        }
        if (jMirrorY) {
            s += 'Y';
        }
        while (jAngle >= 900) {
            s += 'R';
            jAngle -= 900;
        }
        if (jAngle != 0) {
            s = s + jAngle;
        }
        this.jString = s;

    }

    private Object readResolve() {
        return fromJava(jAngle, jMirrorX, jMirrorY);
    }

    /**
     * Get Orientation by the new Java style parameters.
     *
     * @param jAngle the angle of rotation (in tenth-degrees)
     * @param jMirrorX if true, object is flipped over the vertical (mirror in
     * X).
     * @param jMirrorY if true, object is flipped over the horizontal (mirror in
     * Y).
     * @return the Orientation.
     */
    public static Orientation fromJava(int jAngle, boolean jMirrorX, boolean jMirrorY) {
        if (jAngle % 450 == 0) {
            int index = jAngle / 450 & OCTANT;
            if (jMirrorX) {
                index |= XMIRROR45;
            }
            if (jMirrorY) {
                index |= YMIRROR45;
            }
            return map45[index];
        }

        jAngle = jAngle % 3600;
        if (jAngle < 0) {
            jAngle += 3600;
        }
        int index = 0;
        if (jMirrorX) {
            index += 3600;
        }
        if (jMirrorY) {
            index += 3600 * 2;
        }

        Integer key = new Integer(index + jAngle);
        Orientation orient;
        synchronized (map) {
            orient = map.get(key);
            if (orient == null) {
                orient = new Orientation(jAngle, jMirrorX, jMirrorY, null);
                map.put(key, orient);
                if (orient.inverse != orient) {
                    key = new Integer(index + 3600 - jAngle);
                    map.put(key, orient.inverse);
                }
            }
        }
        return orient;
    }

    /**
     * Get Orientation by the old C style parameters.
     *
     * @param cAngle the angle of rotation (in tenth-degrees)
     * @param cTranspose if true, object is flipped over the major diagonal
     * after rotation.
     * @return the Orientation.
     */
    public static Orientation fromC(int cAngle, boolean cTranspose) {
        return fromJava(cTranspose ? cAngle % 3600 + 900 : cAngle, false, cTranspose);
    }

    /**
     * Get Orientation by the angle without mirrors.
     *
     * @param angle the angle of rotation (in tenth-degrees)
     * @return the Orientation.
     */
    public static Orientation fromAngle(int angle) {
        return fromJava(angle, false, false);
    }

    /**
     * Get Orientation by the angle without mirrors.
     *
     * @param numquadrants the angle of rotation (in ninety-degrees)
     * @return the Orientation.
     */
    public static Orientation fromQuadrants(int numquadrants) {
        return fromJava(900 * (numquadrants & 3), false, false);
    }

    /**
     * Return inverse Orientation to this Orientation.
     *
     * @return inverse Orientation.
     */
    public Orientation inverse() {
        return inverse;
    }

    /**
     * Return canonical Orientation to this Orientation.
     *
     * @return canonical Orientation.
     */
    public Orientation canonic() {
        return jMirrorX ? fromC(cAngle, cTranspose) : this;
    }

    /**
     * Concatenates this Orientation with other Orientation. In matrix notation
     * returns this * that.
     *
     * @param that other Orientation.
     * @return concatenation of this and other Orientations.
     */
    public Orientation concatenate(Orientation that) {
        boolean mirrorX = this.jMirrorX ^ that.jMirrorX;
        boolean mirrorY = this.jMirrorY ^ that.jMirrorY;
        int angle = that.jMirrorX ^ that.jMirrorY ? that.jAngle - this.jAngle : that.jAngle + this.jAngle;
        return fromJava(angle, mirrorX, mirrorY);
    }

    /**
     * Method to return the old C style angle value.
     *
     * @return the old C style angle value, in tenth-degrees.
     */
    public int getCAngle() {
        return cAngle;
    }

    /**
     * Method to return the old C style transpose factor.
     *
     * @return the old C style transpose factor: true to flip over the major
     * diagonal after rotation.
     */
    public boolean isCTranspose() {
        return cTranspose;
    }

    /**
     * Method to return the new Java style angle value.
     *
     * @return the new Java style angle value, in tenth-degrees.
     */
    public int getAngle() {
        return jAngle;
    }

    /**
     * Method to return the new Java style Mirror X factor.
     *
     * @return true to flip over the vertical axis (mirror in X).
     */
    public boolean isXMirrored() {
        return jMirrorX;
    }

    /**
     * Method to return the new Java style Mirror Y factor.
     *
     * @return true to flip over the horizontal axis (mirror in Y).
     */
    public boolean isYMirrored() {
        return jMirrorY;
    }

    /**
     * Returns true if orientation is one of Manhattan orientations.
     *
     * @return true if orientation is one of Manhattan orientations.
     */
    public boolean isManhattan() {
        return manh != MNONE;
    }

    /**
     * Returns true if orientation is identical.
     *
     * @return true if orientation is identical.
     */
    public boolean isIdent() {
        return manh == MIDENT;
    }

    /**
     * Method to return a transformation that rotates an object.
     *
     * @return a transformation that rotates by this Orientation.
     */
    public FixpTransform pureRotate() {
        return (FixpTransform) trans.clone();
    }

    /**
     * Method to return an exact determinant of the transformation.
     *
     * @return exact determinant of the transformation.
     */
    public double getDeterminant() {
        return cTranspose ? -1.0 : 1.0;
    }

    /**
     * Method to return type of the transform as defined in
     * {@link java.awt.geom.AffineTransform#getType() }
     *
     * @return type of the transform
     */
    public int getType() {
        return affineTransformType;
    }

    /**
     * Method to return a transformation that rotates an object about a point.
     *
     * @param c the center point about which to rotate.
     * @return a transformation that rotates about that point.
     */
    public FixpTransform rotateAbout(AbstractFixpPoint c) {
        long cX = c.getFixpX();
        long cY = c.getFixpY();
        if (cX != 0 || cY != 0) {
            return new FixpTransform(
                    cX - (long) Math.rint(m00 * cX + m01 * cY),
                    cY - (long) Math.rint(m11 * cY + m10 * cX),
                    this);
        } else {
            return pureRotate();
        }
    }

    /**
     * Method to return a transformation that rotates an object about a point.
     *
     * @param cX the center X coordinate about which to rotate.
     * @param cY the center Y coordinate about which to rotate.
     * @return a transformation that rotates about that point.
     */
    public FixpTransform rotateAbout(double cX, double cY) {
        return rotateAbout(cX, cY, -cX, -cY);
    }

    /**
     * Method to return a transformation that translate an object then rotates
     * and the again translates.
     *
     * @param aX the center X coordinate to translate after rotation.
     * @param aY the center Y coordinate to translate after rotation.
     * @param bX the center X coordinate to translate before rotation.
     * @param bY the center Y coordinate to translate before rotation.
     * @return a transformation that rotates about that point.
     */
    public FixpTransform rotateAbout(double aX, double aY, double bX, double bY) {
        if (bX != 0 || bY != 0) {
            aX = aX + m00 * bX + m01 * bY;
            aY = aY + m11 * bY + m10 * bX;
        }

        return new FixpTransform(FixpCoord.lambdaToFixp(aX), FixpCoord.lambdaToFixp(aY), this);
    }

    /**
     * Method to transform direction by the Orientation.
     *
     * @param angle the angle of initial direction in tenth-degrees.
     * @return angle of transformed direction in tenth-degrees.
     */
    public int transformAngle(int angle) {
        angle += this.cAngle;
        if (cTranspose) {
            angle = 2700 - angle;
        }
        angle %= 3600;
        if (angle < 0) {
            angle += 3600;
        }
        return angle;
    }

    public Point2D transform(long fixpX, long fixpY, Point2D ptSrc, Point2D ptDst) {
        if (ptSrc instanceof AbstractFixpPoint) {
            AbstractFixpPoint fpSrc = (AbstractFixpPoint) ptSrc;
            long srcX = fpSrc.getFixpX();
            long srcY = fpSrc.getFixpY();
            long dstX, dstY;
            switch (manh) {
                case MIDENT:
                    dstX = srcX;
                    dstY = srcY;
                    break;
                case MR:
                    dstX = -srcY;
                    dstY = srcX;
                    break;
                case MRR:
                    dstX = -srcX;
                    dstY = -srcY;
                    break;
                case MRRR:
                    dstX = srcY;
                    dstY = -srcX;
                    break;
                case MY:
                    dstX = srcX;
                    dstY = -srcY;
                    break;
                case MYR:
                    dstX = -srcY;
                    dstY = -srcX;
                    break;
                case MYRR:
                    dstX = -srcX;
                    dstY = srcY;
                    break;
                case MYRRR:
                    dstX = srcY;
                    dstY = srcX;
                    break;
                default:
                    dstX = (long) Math.rint(srcX * m00 + srcY * m01);
                    dstY = (long) Math.rint(srcX * m10 + srcY * m11);
            }
            dstX += fixpX;
            dstY += fixpY;
            if (ptDst == null) {
                ptDst = fpSrc.create(dstX, dstY);
            } else if (ptDst instanceof AbstractFixpPoint) {
                ((AbstractFixpPoint) ptDst).setFixpLocation(dstX, dstY);
            } else {
                ptDst.setLocation(FixpCoord.fixpToLambda(dstX), FixpCoord.fixpToLambda(dstY));
            }
            return ptDst;
        }
        double srcX = ptSrc.getX();
        double srcY = ptSrc.getY();
        double dstX = FixpCoord.fixpToLambda(fixpX) + transformX(srcX, srcY);
        double dstY = FixpCoord.fixpToLambda(fixpY) + transformY(srcX, srcY);
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

    public void transform(long fixpX, long fixpY, AbstractFixpPoint[] ptSrc, int srcOff,
            AbstractFixpPoint[] ptDst, int dstOff,
            int numPts) {
        switch (manh) {
            case MIDENT:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX + src.getFixpX();
                    long dstY = fixpY + src.getFixpY();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            case MR:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX - src.getFixpY();
                    long dstY = fixpY + src.getFixpX();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            case MRR:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX - src.getFixpX();
                    long dstY = fixpY - src.getFixpY();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            case MRRR:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX + src.getFixpY();
                    long dstY = fixpY - src.getFixpX();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            case MY:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX + src.getFixpX();
                    long dstY = fixpY - src.getFixpY();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            case MYR:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX - src.getFixpY();
                    long dstY = fixpY - src.getFixpX();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            case MYRR:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX - src.getFixpX();
                    long dstY = fixpY + src.getFixpY();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            case MYRRR:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long dstX = fixpX + src.getFixpY();
                    long dstY = fixpY + src.getFixpX();
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
                break;
            default:
                while (--numPts >= 0) {
                    AbstractFixpPoint src = ptSrc[srcOff++];
                    long x = src.getFixpX();
                    long y = src.getFixpY();
                    long dstX = fixpX + (long) Math.rint(x * m00 + y * m01);
                    long dstY = fixpY + (long) Math.rint(x * m10 + y * m11);
                    AbstractFixpPoint dst = ptDst[dstOff++];
                    if (dst == null) {
                        ptDst[dstOff - 1] = src.create(dstX, dstY);
                    } else {
                        dst.setFixpLocation(dstX, dstY);
                    }
                }
        }
    }

    /**
     * Calculate points transformed by this Orientation.
     *
     * @param numPoints
     * @param srcCoords coordinates x, y of points.
     */
    public void transformPoints(int numPoints, long[] srcCoords, long[] dstCoords) {
        if (srcCoords == dstCoords) {
            transformPoints(numPoints, srcCoords);
            return;
        }
        switch (manh) {
            case MIDENT:
                System.arraycopy(srcCoords, 0, dstCoords, 0, numPoints * 2);
                return;
            case MR:
                for (int i = 0; i < numPoints; i++) {
                    long x = srcCoords[i * 2 + 0];
                    long y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = -y;
                    dstCoords[i * 2 + 1] = x;
                }
                return;
            case MRR:
                for (int i = 0; i < numPoints; i++) {
                    dstCoords[i * 2 + 0] = -srcCoords[i * 2 + 0];
                    dstCoords[i * 2 + 1] = -srcCoords[i * 2 + 1];
                }
                return;
            case MRRR:
                for (int i = 0; i < numPoints; i++) {
                    long x = srcCoords[i * 2 + 0];
                    long y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = y;
                    dstCoords[i * 2 + 1] = -x;
                }
                return;
            case MY:
                for (int i = 0; i < numPoints; i++) {
                    dstCoords[i * 2 + 0] = srcCoords[i * 2 + 0];
                    dstCoords[i * 2 + 1] = -srcCoords[i * 2 + 1];
                }
                return;
            case MYR:
                for (int i = 0; i < numPoints; i++) {
                    long x = srcCoords[i * 2 + 0];
                    long y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = -y;
                    dstCoords[i * 2 + 1] = -x;
                }
                return;
            case MYRR:
                for (int i = 0; i < numPoints; i++) {
                    dstCoords[i * 2 + 0] = -srcCoords[i * 2 + 0];
                    dstCoords[i * 2 + 1] = srcCoords[i * 2 + 1];
                }
                return;
            case MYRRR:
                for (int i = 0; i < numPoints; i++) {
                    long x = srcCoords[i * 2 + 0];
                    long y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = y;
                    dstCoords[i * 2 + 1] = x;
                }
                return;
            default:
                for (int i = 0; i < numPoints; i++) {
                    long x = srcCoords[i * 2 + 0];
                    long y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = (long) Math.rint(x * m00 + y * m01);
                    dstCoords[i * 2 + 1] = (long) Math.rint(x * m10 + y * m11);
                }
        }
    }

    /**
     * Calculate points transformed by this Orientation.
     *
     * @param numPoints
     * @param srcCoords coordinates x, y of points.
     */
    public void transformPoints(int numPoints, int[] srcCoords, int[] dstCoords) {
        if (srcCoords == dstCoords) {
            transformPoints(numPoints, srcCoords);
            return;
        }
        switch (manh) {
            case MIDENT:
                System.arraycopy(srcCoords, 0, dstCoords, 0, numPoints * 2);
                return;
            case MR:
                for (int i = 0; i < numPoints; i++) {
                    int x = srcCoords[i * 2 + 0];
                    int y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = -y;
                    dstCoords[i * 2 + 1] = x;
                }
                return;
            case MRR:
                for (int i = 0; i < numPoints; i++) {
                    dstCoords[i * 2 + 0] = -srcCoords[i * 2 + 0];
                    dstCoords[i * 2 + 1] = -srcCoords[i * 2 + 1];
                }
                return;
            case MRRR:
                for (int i = 0; i < numPoints; i++) {
                    int x = srcCoords[i * 2 + 0];
                    int y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = y;
                    dstCoords[i * 2 + 1] = -x;
                }
                return;
            case MY:
                for (int i = 0; i < numPoints; i++) {
                    dstCoords[i * 2 + 0] = srcCoords[i * 2 + 0];
                    dstCoords[i * 2 + 1] = -srcCoords[i * 2 + 1];
                }
                return;
            case MYR:
                for (int i = 0; i < numPoints; i++) {
                    int x = srcCoords[i * 2 + 0];
                    int y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = -y;
                    dstCoords[i * 2 + 1] = -x;
                }
                return;
            case MYRR:
                for (int i = 0; i < numPoints; i++) {
                    dstCoords[i * 2 + 0] = -srcCoords[i * 2 + 0];
                    dstCoords[i * 2 + 1] = srcCoords[i * 2 + 1];
                }
                return;
            case MYRRR:
                for (int i = 0; i < numPoints; i++) {
                    int x = srcCoords[i * 2 + 0];
                    int y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = y;
                    dstCoords[i * 2 + 1] = x;
                }
                return;
            default:
                for (int i = 0; i < numPoints; i++) {
                    int x = srcCoords[i * 2 + 0];
                    int y = srcCoords[i * 2 + 1];
                    dstCoords[i * 2 + 0] = (int) Math.rint(x * m00 + y * m01);
                    dstCoords[i * 2 + 1] = (int) Math.rint(x * m10 + y * m11);
                }
        }
    }

    /**
     * Calculate points transformed by this Orientation.
     *
     * @param numPoints
     * @param coords coordinates x, y of points.
     */
    public void transformPoints(int numPoints, long[] coords) {
        switch (manh) {
            case MIDENT:
                return;
            case MR:
                for (int i = 0; i < numPoints; i++) {
                    long x = coords[i * 2 + 0];
                    long y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = -y;
                    coords[i * 2 + 1] = x;
                }
                return;
            case MRR:
                for (int i = 0; i < numPoints; i++) {
                    coords[i * 2 + 0] = -coords[i * 2 + 0];
                    coords[i * 2 + 1] = -coords[i * 2 + 1];
                }
                return;
            case MRRR:
                for (int i = 0; i < numPoints; i++) {
                    long x = coords[i * 2 + 0];
                    long y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = y;
                    coords[i * 2 + 1] = -x;
                }
                return;
            case MY:
                for (int i = 0; i < numPoints; i++) {
                    coords[i * 2 + 1] = -coords[i * 2 + 1];
                }
                return;
            case MYR:
                for (int i = 0; i < numPoints; i++) {
                    long x = coords[i * 2 + 0];
                    long y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = -y;
                    coords[i * 2 + 1] = -x;
                }
                return;
            case MYRR:
                for (int i = 0; i < numPoints; i++) {
                    coords[i * 2 + 0] = -coords[i * 2 + 0];
                }
                return;
            case MYRRR:
                for (int i = 0; i < numPoints; i++) {
                    long x = coords[i * 2 + 0];
                    long y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = y;
                    coords[i * 2 + 1] = x;
                }
                return;
            default:
                for (int i = 0; i < numPoints; i++) {
                    long x = coords[i * 2 + 0];
                    long y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = (long) Math.rint(x * m00 + y * m01);
                    coords[i * 2 + 1] = (long) Math.rint(x * m10 + y * m11);
                }
        }
    }

    /**
     * Calculate points transformed by this Orientation.
     *
     * @param numPoints
     * @param coords coordinates x, y of points.
     */
    public void transformPoints(int numPoints, int[] coords) {
        switch (manh) {
            case MIDENT:
                return;
            case MR:
                for (int i = 0; i < numPoints; i++) {
                    int x = coords[i * 2 + 0];
                    int y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = -y;
                    coords[i * 2 + 1] = x;
                }
                return;
            case MRR:
                for (int i = 0; i < numPoints; i++) {
                    coords[i * 2 + 0] = -coords[i * 2 + 0];
                    coords[i * 2 + 1] = -coords[i * 2 + 1];
                }
                return;
            case MRRR:
                for (int i = 0; i < numPoints; i++) {
                    int x = coords[i * 2 + 0];
                    int y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = y;
                    coords[i * 2 + 1] = -x;
                }
                return;
            case MY:
                for (int i = 0; i < numPoints; i++) {
                    coords[i * 2 + 1] = -coords[i * 2 + 1];
                }
                return;
            case MYR:
                for (int i = 0; i < numPoints; i++) {
                    int x = coords[i * 2 + 0];
                    int y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = -y;
                    coords[i * 2 + 1] = -x;
                }
                return;
            case MYRR:
                for (int i = 0; i < numPoints; i++) {
                    coords[i * 2 + 0] = -coords[i * 2 + 0];
                }
                return;
            case MYRRR:
                for (int i = 0; i < numPoints; i++) {
                    int x = coords[i * 2 + 0];
                    int y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = y;
                    coords[i * 2 + 1] = x;
                }
                return;
            default:
                for (int i = 0; i < numPoints; i++) {
                    int x = coords[i * 2 + 0];
                    int y = coords[i * 2 + 1];
                    coords[i * 2 + 0] = (int) Math.rint(x * m00 + y * m01);
                    coords[i * 2 + 1] = (int) Math.rint(x * m10 + y * m11);
                }
        }
    }

    /**
     * Calculate X-coordinate transformation of a Point by this Orientation.
     *
     * @param x X-coordinate of the Point
     * @param y Y-coordinate of the Point
     * @return transformed X-coordinate
     */
    public long transformX(long x, long y) {
        switch (manh) {
            case MIDENT:
                return x;
            case MR:
                return -y;
            case MRR:
                return -x;
            case MRRR:
                return y;
            case MY:
                return x;
            case MYR:
                return -y;
            case MYRR:
                return -x;
            case MYRRR:
                return y;
            default:
                return (long) Math.rint(x * m00 + y * m01);
        }
    }

    /**
     * Calculate Y-coordinate transformation of a Point by this Orientation.
     *
     * @param x X-coordinate of the Point
     * @param y Y-coordinate of the Point
     * @return transformed Y-coordinate
     */
    public long transformY(long x, long y) {
        switch (manh) {
            case MIDENT:
                return y;
            case MR:
                return x;
            case MRR:
                return -y;
            case MRRR:
                return -x;
            case MY:
                return -y;
            case MYR:
                return -x;
            case MYRR:
                return y;
            case MYRRR:
                return x;
            default:
                return (long) Math.rint(x * m10 + y * m11);
        }

    }

    /**
     * Calculate X-coordinate transformation of a Point by this Orientation.
     *
     * @param x X-coordinate of the Point
     * @param y Y-coordinate of the Point
     * @return transformed X-coordinate
     */
    public double transformX(double x, double y) {
        switch (manh) {
            case MIDENT:
                return x;
            case MR:
                return -y;
            case MRR:
                return -x;
            case MRRR:
                return y;
            case MY:
                return x;
            case MYR:
                return -y;
            case MYRR:
                return -x;
            case MYRRR:
                return y;
            default:
                return x * m00 + y * m01;
        }
    }

    /**
     * Calculate Y-coordinate transformation of a Point by this Orientation.
     *
     * @param x X-coordinate of the Point
     * @param y Y-coordinate of the Point
     * @return transformed Y-coordinate
     */
    public double transformY(double x, double y) {
        switch (manh) {
            case MIDENT:
                return y;
            case MR:
                return x;
            case MRR:
                return -y;
            case MRRR:
                return -x;
            case MY:
                return -y;
            case MYR:
                return -x;
            case MYRR:
                return y;
            case MYRRR:
                return x;
            default:
                return x * m10 + y * m11;
        }

    }

    /**
     * Calculate the transformation of a Point by this Orientation.
     *
     * @param coord the point to transform.
     * @return the point rotated about this Orientation.
     */
    public Point2D transformPoint(Point2D coord) {
        switch (manh) {
            case MIDENT:
                return coord;
            case MR:
                return new Point2D.Double(-coord.getY(), coord.getX());
            case MRR:
                return new Point2D.Double(-coord.getX(), -coord.getY());
            case MRRR:
                return new Point2D.Double(coord.getY(), -coord.getX());
            case MY:
                return new Point2D.Double(coord.getX(), -coord.getY());
            case MYR:
                return new Point2D.Double(-coord.getY(), -coord.getX());
            case MYRR:
                return new Point2D.Double(-coord.getX(), coord.getY());
            case MYRRR:
                return new Point2D.Double(coord.getY(), coord.getX());
            default:
                Point2D.Double result = new Point2D.Double();
                trans.transform(coord, result);
                return result;
        }
    }

    /**
     * Calculate bounds of rectangle transformed by this Orientation.
     *
     * @param src rectangle.
     * @param c shift
     * @param dst destination rectangle.
     */
    public void rectangleBounds(Rectangle2D src, Point2D c, Rectangle2D dst) {
        rectangleBounds(src.getMinX(), src.getMinY(), src.getMaxX(), src.getMaxY(), c.getX(), c.getY(), dst);
    }

    /**
     * Calculate bounds of rectangle transformed by this Orientation.
     *
     * @param src rectangle.
     * @param fixpCX shift X in fixed-point units
     * @param fixpCY shift Y in fixed-point units
     * @param dst destination rectangle.
     */
    public void rectangleBounds(AbstractFixpRectangle src, long fixpCX, long fixpCY, AbstractFixpRectangle dst) {
        long xl = src.getFixpMinX();
        long yl = src.getFixpMinY();
        long xh = src.getFixpMaxX();
        long yh = src.getFixpMaxY();
        switch (manh) {
            case MIDENT:
                dst.setFixp(fixpCX + xl, fixpCY + yl, fixpCX + xh, fixpCY + yh);
                return;
            case MR:
                dst.setFixp(fixpCX - yh, fixpCY + xl, fixpCX - yl, fixpCY + xh);
                return;
            case MRR:
                dst.setFixp(fixpCX - xh, fixpCY - yh, fixpCX - xl, fixpCY - yl);
                return;
            case MRRR:
                dst.setFixp(fixpCX + yl, fixpCY - xh, fixpCX + yh, fixpCY - xl);
                return;
            case MY:
                dst.setFixp(fixpCX + xl, fixpCY - yh, fixpCX + xh, fixpCY - yl);
                return;
            case MYR:
                dst.setFixp(fixpCX - yh, fixpCY - xh, fixpCX - yl, fixpCY - xl);
                return;
            case MYRR:
                dst.setFixp(fixpCX - xh, fixpCY + yl, fixpCX - xl, fixpCY + yh);
                return;
            case MYRRR:
                dst.setFixp(fixpCX + yl, fixpCY + xl, fixpCX + yh, fixpCY + xh);
                return;
            default:
                assert (manh == MNONE);
                dst.setFixp(
                        fixpCX + (long) Math.rint(m00 * (m00 >= 0 ? xl : xh) + m01 * (m01 >= 0 ? yl : yh)),
                        fixpCY + (long) Math.rint(m10 * (m10 >= 0 ? xl : xh) + m11 * (m11 >= 0 ? yl : yh)),
                        fixpCX + (long) Math.rint(m00 * (m00 >= 0 ? xh : xl) + m01 * (m01 >= 0 ? yh : yl)),
                        fixpCY + (long) Math.rint(m10 * (m10 >= 0 ? xh : xl) + m11 * (m11 >= 0 ? yh : yl)));
        }
    }

    /**
     * Calculate bounds of rectangle transformed by this Orientation.
     *
     * @param xl lower x coordinate.
     * @param yl lower y coordinate.
     * @param xh higher x coordinate.
     * @param yh higher y coordinate.
     * @param cx additional x shift
     * @param cy additional y shift.
     * @param dst destination rectangle.
     */
    public void rectangleBounds(double xl, double yl, double xh, double yh, double cx, double cy, Rectangle2D dst) {
        double dx = xh - xl;
        double dy = yh - yl;
        switch (manh) {
            case MIDENT:
                dst.setFrame(cx + xl, cy + yl, dx, dy);
                return;
            case MR:
                dst.setFrame(cx - yh, cy + xl, dy, dx);
                return;
            case MRR:
                dst.setFrame(cx - xh, cy - yh, dx, dy);
                return;
            case MRRR:
                dst.setFrame(cx + yl, cy - xh, dy, dx);
                return;
            case MY:
                dst.setFrame(cx + xl, cy - yh, dx, dy);
                return;
            case MYR:
                dst.setFrame(cx - yh, cy - xh, dy, dx);
                return;
            case MYRR:
                dst.setFrame(cx - xh, cy + yl, dx, dy);
                return;
            case MYRRR:
                dst.setFrame(cx + yl, cy + xl, dy, dx);
                return;
        }
        assert manh == MNONE;
        dst.setFrame(cx + m00 * (m00 >= 0 ? xl : xh) + m01 * (m01 >= 0 ? yl : yh),
                cy + m10 * (m10 >= 0 ? xl : xh) + m11 * (m11 >= 0 ? yl : yh),
                Math.abs(m00) * dx + Math.abs(m01) * dy,
                Math.abs(m10) * dx + Math.abs(m11) * dy);
    }

    /**
     * Calculate bounds of rectangle transformed by this Orientation.
     *
     * @param coords coordinates [0]=low X, [1]=low Y, [2]=high X, [3]=high Y.
     */
    public void rectangleBounds(long[] coords) {
        long xl = coords[0];
        long yl = coords[1];
        long xh = coords[2];
        long yh = coords[3];
        switch (manh) {
            case MIDENT:
                return;
            case MR:
                coords[0] = -yh;
                coords[1] = xl;
                coords[2] = -yl;
                coords[3] = xh;
                return;
            case MRR:
                coords[0] = -xh;
                coords[1] = -yh;
                coords[2] = -xl;
                coords[3] = -yl;
                return;
            case MRRR:
                coords[0] = yl;
                coords[1] = -xh;
                coords[2] = yh;
                coords[3] = -xl;
                return;
            case MY:
                coords[1] = -yh;
                coords[3] = -yl;
                return;
            case MYR:
                coords[0] = -yh;
                coords[1] = -xh;
                coords[2] = -yl;
                coords[3] = -xl;
                return;
            case MYRR:
                coords[0] = -xh;
                coords[2] = -xl;
                return;
            case MYRRR:
                coords[0] = yl;
                coords[1] = xl;
                coords[2] = yh;
                coords[3] = xh;
                return;
            default:
                assert manh == MNONE;
                coords[0] = (long) Math.rint(m00 * (m00 >= 0 ? xl : xh) + m01 * (m01 >= 0 ? yl : yh));
                coords[1] = (long) Math.rint(m10 * (m10 >= 0 ? xl : xh) + m11 * (m11 >= 0 ? yl : yh));
                coords[2] = (long) Math.rint(m00 * (m00 >= 0 ? xh : xl) + m01 * (m01 >= 0 ? yh : yl));
                coords[3] = (long) Math.rint(m10 * (m10 >= 0 ? xh : xl) + m11 * (m11 >= 0 ? yh : yl));
        }
    }

    /**
     * Calculate bounds of rectangle transformed by this Orientation.
     *
     * @param coords coordinates [0]=low X, [1]=low Y, [2]=high X, [3]=high Y.
     */
    public void rectangleBounds(int[] coords) {
        int xl = coords[0];
        int yl = coords[1];
        int xh = coords[2];
        int yh = coords[3];
        switch (manh) {
            case MIDENT:
                return;
            case MR:
                coords[0] = -yh;
                coords[1] = xl;
                coords[2] = -yl;
                coords[3] = xh;
                return;
            case MRR:
                coords[0] = -xh;
                coords[1] = -yh;
                coords[2] = -xl;
                coords[3] = -yl;
                return;
            case MRRR:
                coords[0] = yl;
                coords[1] = -xh;
                coords[2] = yh;
                coords[3] = -xl;
                return;
            case MY:
                coords[1] = -yh;
                coords[3] = -yl;
                return;
            case MYR:
                coords[0] = -yh;
                coords[1] = -xh;
                coords[2] = -yl;
                coords[3] = -xl;
                return;
            case MYRR:
                coords[0] = -xh;
                coords[2] = -xl;
                return;
            case MYRRR:
                coords[0] = yl;
                coords[1] = xl;
                coords[2] = yh;
                coords[3] = xh;
                return;
            default:
                assert manh == MNONE;
                coords[0] = (int) Math.rint(m00 * (m00 >= 0 ? xl : xh) + m01 * (m01 >= 0 ? yl : yh));
                coords[1] = (int) Math.rint(m10 * (m10 >= 0 ? xl : xh) + m11 * (m11 >= 0 ? yl : yh));
                coords[2] = (int) Math.rint(m00 * (m00 >= 0 ? xh : xl) + m01 * (m01 >= 0 ? yh : yl));
                coords[3] = (int) Math.rint(m10 * (m10 >= 0 ? xh : xl) + m11 * (m11 >= 0 ? yh : yl));
        }
    }

    /**
     * Returns string which represents this Orientation in JELIB format.
     *
     * @return string in JELIB format.
     */
    public String toJelibString() {
        return jString;
    }

    /**
     * Returns text representation of this Orientation.
     *
     * @return text representation of this Orientation.
     */
    @Override
    public String toString() {
        return toJelibString();
    }
}
