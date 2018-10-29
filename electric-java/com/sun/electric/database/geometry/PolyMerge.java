/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PolyMerge.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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

import com.sun.electric.technology.Layer;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.GenMath;
import com.sun.electric.util.math.MutableBoolean;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * This is the Polygon Merging facility.
 * <P>
 * Initially, call:<BR>
 *    PolyMerge merge = new PolyMerge();<BR>
 * The returned value is used in subsequent calls.
 * <P>
 * For every polygon, call:<BR>
 *    merge.addPolygon(layer, poly);<BR>
 * where "layer" is a layer and "poly" is a polygon to be added.
 * <P>
 * You can also subtract a polygon by calling:<BR>
 *    merge.subtract(layer, poly);<BR>
 * <P>
 * To combine two different merges, use:<BR>
 *    merge.addMerge(addmerge, trans)<BR>
 * to add the merge information in "addmerge" (transformed by "trans")
 * <P>
 * At end of merging, call:<BR>
 *    merge.getMergedPoints(layer)<BR>
 * for each layer, and it returns an array of PolyBases on that layer.
 */
public class PolyMerge extends GeometryHandler implements Serializable
{
	/**
	 * Method to create a new "merge" object.
	 */
	public PolyMerge()
	{
	}

	/**
	 * Method to add a PolyBase to the merged collection.
	 * @param key the layer that this PolyBase sits on.
	 * @param value the PolyBase to merge. If value is only Shape type
     * then it would take the bounding box. This might not be precise enough!.
	 */
	public void add(Layer key, Object value)
	{
		Layer layer = key;
        PolyBase poly;
        if (value instanceof PolyBase)
		    poly = (PolyBase)value;
        else if (value instanceof Shape)
            poly = new PolyBase(((Shape)value).getBounds2D());
        else
            return;
		addPolygon(layer, poly);
	}

	/**
	 * Method to add a Rectangle to the merged collection.
	 * @param layer the layer that this Poly sits on.
	 * @param rect the Rectangle to merge.
	 */
	public void addRectangle(Layer layer, Rectangle2D rect)
	{
		Area area = (Area)layers.get(layer);
		if (area == null)
		{
			area = new Area();
			layers.put(layer, area);
		}

		// add "rect" to "area"
		Area additionalArea = new Area(rect);
		area.add(additionalArea);
	}

	/**
	 * Method to add a PolyBase to the merged collection.
	 * @param layer the layer that this Poly sits on.
	 * @param poly the PolyBase to merge.
	 */
	public void addPolygon(Layer layer, PolyBase poly)
	{
		Area area = (Area)layers.get(layer);
		if (area == null)
		{
			area = new Area();
			layers.put(layer, area);
		}

		// add "poly" to "area"
		// It can't add only rectangles otherwise it doesn't cover
		// serpentine transistors.
		Area additionalArea = new Area(poly);
		area.add(additionalArea);
	}

	/**
	 * Method to subtract a PolyBase from the merged collection.
	 * @param layer the layer that this PolyBase sits on.
	 * @param poly the PolyBase to merge.
	 */
	public void subtract(Object layer, Object poly)
	{
		Area area = (Area)layers.get(layer);
		if (area == null) return;
		Area subtractArea = new Area((PolyBase)poly);
		area.subtract(subtractArea);
	}

	/**
	 * Method to add another Merge to this one.
	 * @param subMerge the other Merge to add in.
	 * @param trans a transformation on the other Merge.
	 */
	public void addAll(GeometryHandler subMerge, FixpTransform trans)
	{
		PolyMerge other = (PolyMerge)subMerge;
		addMerge(other, trans);
	}

	/**
	 * Method to add another Merge to this one.
	 * @param other the other Merge to add in.
	 * @param trans a transformation on the other Merge.
	 */
	public void addMerge(PolyMerge other, FixpTransform trans)
	{
		for(Layer subLayer : other.layers.keySet())
		{
			Area subArea = (Area)other.layers.get(subLayer);

			Area area = (Area)layers.get(subLayer);
			if (area == null)
			{
				area = new Area();
				layers.put(subLayer, area);
			}
			Area newArea = subArea.createTransformedArea(trans);
			area.add(newArea);
		}
	}

	/**
	 * Method to add one Layer to another in this merge.
	 * @param fromLayer the other Layer to add in.
	 * @param toLayer the destination layer that will contain the union of itself and "fromLayer".
	 */
	public void addLayer(Layer fromLayer, Layer toLayer)
	{
		Area fromArea = (Area)layers.get(fromLayer);
		if (fromArea == null) return;

		Area toArea = (Area)layers.get(toLayer);
		if (toArea == null)
		{
			toArea = new Area(fromArea);
			layers.put(toLayer, toArea);
			return;
		}

		toArea.add(fromArea);
	}

	/**
	 * Method to determine whether a polygon intersects a layer in the merge.
	 * @param layer the layer to test.
	 * @param poly the polygon to examine.
	 * @return true if any part of the polygon exists in that layer.
	 */
	public boolean intersects(Layer layer, PolyBase poly)
	{
		Area layerArea = (Area)layers.get(layer);
		if (layerArea == null) return false;

		// simple calculation for manhattan polygon
		Rectangle2D box = poly.getBox();
		if (box != null)
		{
			return layerArea.intersects(box);
		}

		// more complex calculation (not done yet)
		Area intersectArea = new Area(poly);
		intersectArea.intersect(layerArea);
		return !intersectArea.isEmpty();
	}

	/**
	 * Method to intersect two layers in this merge and produce a third.
	 * @param sourceA the first Layer to intersect.
	 * @param sourceB the second Layer to intersect.
	 * @param dest the destination layer to place the intersection of the first two.
	 * If there is no intersection, all geometry on this layer is cleared.
	 */
	public void intersectLayers(Layer sourceA, Layer sourceB, Layer dest)
	{
		Area destArea = null;
		Area sourceAreaA = (Area)layers.get(sourceA);
		if (sourceAreaA != null)
		{
			Area sourceAreaB = (Area)layers.get(sourceB);
			if (sourceAreaB != null)
			{
				destArea = new Area(sourceAreaA);
				destArea.intersect(sourceAreaB);
				if (destArea.isEmpty()) destArea = null;
			}
		}
		if (destArea == null) layers.remove(dest); else
			layers.put(dest, destArea);
	}

	/**
	 * Method to subtract one layer from another and produce a third.
	 * @param sourceA the first Layer.
	 * @param sourceB the second Layer, which gets subtracted from the first.
	 * @param dest the destination layer to place the sourceA - sourceB.
	 * If there is nothing left, all geometry on the layer is cleared.
	 */
	public void subtractLayers(Layer sourceA, Layer sourceB, Layer dest)
	{
		Area destArea = null;
		Area sourceAreaA = (Area)layers.get(sourceA);
		if (sourceAreaA != null)
		{
			Area sourceAreaB = (Area)layers.get(sourceB);
			if (sourceAreaB != null)
			{
				destArea = new Area(sourceAreaA);
				destArea.subtract(sourceAreaB);
				if (destArea.isEmpty()) destArea = null;
			}
		}
		if (destArea == null) layers.remove(dest); else
			layers.put(dest, destArea);
	}

	/**
	 * Method to subtract another Merge to this one.
	 * @param other the other Merge to subtract.
	 */
	public void subtractMerge(PolyMerge other)
	{
		for(Layer subLayer : other.layers.keySet())
		{
			Area area = (Area)layers.get(subLayer);
			if (area == null) continue;

			Area subArea = (Area)other.layers.get(subLayer);
			area.subtract(subArea);
		}
	}

	/**
	 * Method to inset one layer by a given amount and create a second layer.
	 * @param source the Layer to inset.
	 * @param dest the destination layer to place the inset geometry.
	 * @param amount the distance to inset the layer.
	 */
	public void insetLayer(Layer source, Layer dest, double amount)
	{
		Area sourceArea = (Area)layers.get(source);
		if (sourceArea == null) layers.remove(dest); else
		{
			layers.put(dest, sourceArea.clone());
			if (amount == 0) return;
			List<PolyBase> orig = getAreaPoints(sourceArea, source, true);
			for(PolyBase poly : orig)
			{
				PolyBase.Point [] points = poly.getPoints();
				for(int i=0; i<points.length; i++)
				{
					int last = i-1;
					if (last < 0) last = points.length-1;
					PolyBase.Point lastPt = points[last];
					PolyBase.Point thisPt = points[i];
					if (DBMath.areEquals(lastPt, thisPt)) continue;
					int angle = DBMath.figureAngle(lastPt, thisPt);
					int perpAngle = (angle + 2700) % 3600;
					double offsetX = DBMath.cos(perpAngle) * amount;
					double offsetY = DBMath.sin(perpAngle) * amount;
					PolyBase.Point insetLastPt = PolyBase.fromLambda(lastPt.getX() + offsetX, lastPt.getY() + offsetY);
					PolyBase.Point insetThisPt = PolyBase.fromLambda(thisPt.getX() + offsetX, thisPt.getY() + offsetY);
					PolyBase subtractPoly = new PolyBase(lastPt, thisPt, insetThisPt, insetLastPt);
					subtract(dest, subtractPoly);
				}
			}
		}
	}

	/**
	 * Method to delete all geometry on a given layer.
	 * @param layer the Layer to clear in this merge.
	 */
	public void deleteLayer(Layer layer)
	{
		layers.remove(layer);
	}

	/**
	 * Method to tell whether there is any valid geometry on a given layer of this merge.
	 * @param layer the layer to test.
	 * @return true if there is no valid geometry on the given layer in this merge.
	 */
	public boolean isEmpty(Layer layer)
	{
		Area area = (Area)layers.get(layer);
		if (area == null) return true;
		return area.isEmpty();
	}

	/**
	 * Method to determine whether a rectangle exists in the merge.
	 * @param layer the layer being tested.
	 * @param rect the rectangle being tested.
	 * @return true if all of the rectangle is inside of the merge on the given layer.
	 */
	public boolean contains(Layer layer, Rectangle2D rect)
	{
		Area area = (Area)layers.get(layer);
		if (area == null) return false;
		if (area.contains(rect)) return true;

		Area rectArea = new Area(rect);
		rectArea.subtract(area);

		// if the new area is empty, then the poly is completely contained in the merge
		if (rectArea.isEmpty()) return true;
		double remainingArea = getAreaOfArea(rectArea);
		if (DBMath.areEquals(remainingArea, 0)) return true;

		return false;
	}

	/**
	 * Method to determine whether a polygon exists in the merge.
	 * @param layer the layer being tested.
	 * @param poly the polygon being tested.
	 * @return true if all of the polygon is inside of the merge on the given layer.
	 */
	public boolean contains(Layer layer, PolyBase poly)
	{
		// find the area for the given layer
		Area area = (Area)layers.get(layer);
		if (area == null) return false;

		// create an area that is the new polygon minus the original area
		Area polyArea = new Area(poly);
		polyArea.subtract(area);

		// if the new area is empty, then the poly is completely contained in the merge
		if (polyArea.isEmpty()) return true;
		double remainingArea = getAreaOfArea(polyArea);
		if (DBMath.areEquals(remainingArea, 0)) return true;
		return false;
	}

    public Area exclusive(Layer layer, PolyBase poly)
    {
        // find the area for the given layer
		Area area = (Area)layers.get(layer);
		if (area == null) return null;

        // create an area that is the new polygon minus the original area
		Area polyArea = new Area(poly);
		polyArea.subtract(area);

        return polyArea;
    }

    /**
	 * Method to see if an arc fits in this merge with or without end extension.
	 * @param layer the layer of the arc being examined.
	 * @param headLoc the head location of the arc.
	 * @param tailLoc the tail location of the arc.
	 * @param wid the width of the arc.
	 * @param headExtend the head extension of the arc (is set false if extension not possible).
	 * @param tailExtend the tail extension of the arc (is set false if extension not possible).
	 * @return true if the arc fits in the merge.  May change "noHeadExtend" and "noTailExtend".
	 * Returns false if the arc cannot fit.
	 */
	public boolean arcPolyFits(Layer layer, Point2D headLoc, Point2D tailLoc, double wid,
		MutableBoolean headExtend, MutableBoolean tailExtend)
	{
		// try arc with default end extension
		int ang = 0;
		if (headLoc.getX() != tailLoc.getX() || headLoc.getY() != tailLoc.getY())
			ang = GenMath.figureAngle(tailLoc, headLoc);
		double endExtensionHead = headExtend.booleanValue() ? wid/2 : 0;
		double endExtensionTail = tailExtend.booleanValue() ? wid/2 : 0;
		Poly arcPoly = Poly.makeEndPointPoly(headLoc.distance(tailLoc), wid, ang, headLoc, endExtensionHead,
			tailLoc, endExtensionTail, Poly.Type.FILLED);
		if (contains(layer, arcPoly)) return true;

		// try removing head extension
		if (headExtend.booleanValue())
		{
			arcPoly = Poly.makeEndPointPoly(headLoc.distance(tailLoc), wid, ang, headLoc, 0,
				tailLoc, endExtensionTail, Poly.Type.FILLED);
			if (contains(layer, arcPoly)) { headExtend.setValue(false);   return true; }
		}

		// try removing tail extension
		if (tailExtend.booleanValue())
		{
			arcPoly = Poly.makeEndPointPoly(headLoc.distance(tailLoc), wid, ang, headLoc, endExtensionHead,
				tailLoc, 0, Poly.Type.FILLED);
			if (contains(layer, arcPoly)) { tailExtend.setValue(false);   return true; }
		}

		// try removing head and tail extension
		if (headExtend.booleanValue() && tailExtend.booleanValue())
		{
			arcPoly = Poly.makeEndPointPoly(headLoc.distance(tailLoc), wid, ang, headLoc, 0,
				tailLoc, 0, Poly.Type.FILLED);
			if (contains(layer, arcPoly)) { headExtend.setValue(false);   tailExtend.setValue(false);   return true; }
		}

		return false;
	}

	/**
	 * Method to return the area on a given layer.
	 * @param layer the layer to query.
	 * @return the area of geometry on the given layer.
	 */
	public double getAreaOfLayer(Layer layer)
	{
		Area area = (Area)layers.get(layer);
		if (area == null) return 0;
		return getAreaOfArea(area);
	}

	private double getAreaOfArea(Area area)
	{
		List<PolyBase> pointList = getAreaPoints(area, null, true);
		double totalArea = 0;
		for(PolyBase p : pointList)
		{
			totalArea += p.getArea();
		}
		return totalArea;
	}

	/**
	 * Method to determine whether a point exists in the merge.
	 * @param layer the layer being tested.
	 * @param pt the point being tested.
	 * @return true if the point is inside of the merge on the given layer.
	 */
	public boolean contains(Layer layer, Point2D pt)
	{
		Area area = (Area)layers.get(layer);
		if (area == null) return false;
		return area.contains(pt);
	}

	public Collection<PolyBase> getObjects(Object layer, boolean modified, boolean simple)
	{
		// Since simple is used, correct detection of loops must be guaranteed
		// outside.
		return getMergedPoints((Layer)layer, simple);
	}

    /**
	 * Method to return list of Polys on a given Layer in this Merge.
	 * @param layer the layer in question.
	 * @param simple
	 * @return the list of Polys that describes this Merge.
	 */
    public List<PolyBase> getMergedPoints(Layer layer, boolean simple)
	{
		Area area = (Area)layers.get(layer);
		if (area == null) return null;
		return getAreaPoints(area, layer, simple);
	}

	/**
	 * Method to return a list of polygons in this merge for a given layer.
	 * @param area the Area object that describes the merge.
	 * @param layer the desired Layer.
	 * @param simple true for simple polygons, false to allow complex ones.
	 * @return a List of PolyBase objects that describes the layer in the merge.
	 */
    public static List<PolyBase> getAreaPoints(Area area, Layer layer, boolean simple)
    {
        return PolyBase.getPointsInArea(area, layer, simple, true);
	}
}
