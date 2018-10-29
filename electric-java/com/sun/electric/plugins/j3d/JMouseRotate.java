/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: JMouseRotate.java
 * Written by Gilda Garreton.
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

package com.sun.electric.plugins.j3d;

import com.sun.j3d.utils.behaviors.mouse.MouseRotate;

import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import javax.media.j3d.Transform3D;

/**
 * Extending original rotation class to allow rotation not from original behavior
 * @author  Gilda Garreton
 * @version 0.1
 */
public class JMouseRotate extends MouseRotate
{
    private Transform3D transformZ = new Transform3D();
    public JMouseRotate(int flags) {super(flags);}

    public void setRotation(double angleX, double angleY, double angleZ)
    {
        // Rotations are in the opposite direction
        transformX.rotX(-1*angleX);
        transformY.rotY(-1*angleY);
        transformZ.rotZ(-1*angleZ);

        transformGroup.getTransform(currXform);

        Matrix4d mat = new Matrix4d();
        // Remember old matrix
        currXform.get(mat);

        // Translate to origin
        currXform.setTranslation(new Vector3d(0.0,0.0,0.0));
        if (invert) {
            currXform.mul(currXform, transformX);
            currXform.mul(currXform, transformY);
            currXform.mul(currXform, transformZ);
        } else {
            currXform.mul(transformX, currXform);
            currXform.mul(transformY, currXform);
            currXform.mul(transformZ, currXform);
        }

        // Set old translation back
        Vector3d translation = new
        Vector3d(mat.m03, mat.m13, mat.m23);
        currXform.setTranslation(translation);

        // Update xform
        transformGroup.setTransform(currXform);

        transformChanged( currXform );
    }
}
