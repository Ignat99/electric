/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: FindEmptyRects.java
 * Written by Steven M. Rubin
 *
 * Copyright (c) 2013, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.placement.general;

import com.sun.electric.database.geometry.ERectangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Class to find the empty rectangles in a cell, given full rectangles.
 */
public class FindEmptyRects
{
	public FindEmptyRects() {}

	/**
	 * Main method of the class.
	 * Finds all empty rectangles inside of an area, given the full rectangles.
	 * @param rectangles the full rectangles.
	 * @param bound the bound in which to work.
	 * @return a List of ERectangle objects that cover all of the empty space.
	 * The rectangles may overlap each other.
	 */
	public List<ERectangle> findEmptySpace(List<ERectangle> rectangles, ERectangle bound)
	{
//		// first make sure the rectangles don't intersect each other
//		for(int i=0; i<rectangles.size(); i++)
//		{
//			ERectangle rect1 = rectangles.get(i);
//			for(int j=i+1; j<rectangles.size(); j++)
//			{
//				ERectangle rect2 = rectangles.get(j);
//				if (rect1.getMaxX() <= rect2.getMinX() || rect1.getMinX() >= rect2.getMaxX() ||
//					rect1.getMaxY() <= rect2.getMinY() || rect1.getMinY() >= rect2.getMaxY()) continue;
//				System.out.println("INTERNAL ERROR: rectangle "+rect1.getMinX()+"<=X<="+rect1.getMaxX()+" / "+rect1.getMinY()+"<=Y<="+rect1.getMaxY()+
//					" intersects other rectangle "+rect2.getMinX()+"<=X<="+rect2.getMaxX()+" / "+rect2.getMinY()+"<=Y<="+rect2.getMaxY());
//			}
//		}

		// make a list of empty rectangles in the cell
		List<ERectangle> emptyRects = new ArrayList<ERectangle>();
		for (ERectangle rect : rectangles)
		{
			double segLX = rect.getMinX();
			double segHX = rect.getMaxX();
			double segLY = rect.getMinY();
			double segHY = rect.getMaxY();
			LineSeg bottom = new LineSeg(segLX, segLY, segHX, segLY);
			buildEmptyRectsBelow(bottom, rect, rectangles, bound, emptyRects);

			LineSeg left = new LineSeg(segLX, segLY, segLX, segHY);
			buildEmptyRectsLeft(left, rect, rectangles, bound, emptyRects);
		}
		LineSeg bottom = new LineSeg(bound.getMinX(), bound.getMaxY(), bound.getMaxX(), bound.getMaxY());
		buildEmptyRectsBelow(bottom, null, rectangles, bound, emptyRects);
		LineSeg left = new LineSeg(bound.getMaxX(), bound.getMinY(), bound.getMaxX(), bound.getMaxY());
		buildEmptyRectsLeft(left, null, rectangles, bound, emptyRects);

		// now reduce the rectangles, removing redundancy
		Collections.sort(emptyRects, new SortRectsBySize());
		for(int i=0; i<emptyRects.size(); i++)
		{
			ERectangle big = emptyRects.get(i);
			for(int j=i+1; j<emptyRects.size(); j++)
			{
				ERectangle small = emptyRects.get(j);
				if (small.getMinX() >= big.getMinX() && small.getMaxX() <= big.getMaxX() &&
					small.getMinY() >= big.getMinY() && small.getMaxY() <= big.getMaxY())
				{
					emptyRects.remove(i);
					j--;
				}
			}
		}
		return emptyRects;
	}

	/**
	 * Method to find all empty rectangles below a line segment.
	 * @param bottom the LineSeg to examine.
	 * @param rect the ERectangle that this LineSeg comes from (may be null for the top edge).
	 * @param rectangles a list of full ERectangles.
	 * @param bound the boundary of the entire Cell (limits the empty space).
	 * @param emptyRects the list of empty rectangles to build up.
	 */
	private void buildEmptyRectsBelow(LineSeg bottom, ERectangle rect, List<ERectangle> rectangles, ERectangle bound, List<ERectangle> emptyRects)
	{
		double segLX = bottom.getMinX();
		double segHX = bottom.getMaxX();
		double segY = bottom.getMinY();
		List<LineSeg> segsBelow = getSegmentsBelow(bottom, rect, rectangles);
		segsBelow.add(new LineSeg(bound.getMinX(), bound.getMinY(), bound.getMaxX(), bound.getMinY()));
		Collections.sort(segsBelow, new SortLineSegsDown());

		// merge adjoining segments
		for(int i=0; i<segsBelow.size(); i++)
		{
			for(int j=i+1; j<segsBelow.size(); j++)
			{
				LineSeg thisSeg = segsBelow.get(i);
				LineSeg nextSeg = segsBelow.get(j);
				if (thisSeg.y1 != nextSeg.y1) break;
				LineSeg adjoin = thisSeg.adjoins(nextSeg);
				if (adjoin != null)
				{
					segsBelow.set(i, adjoin);
					segsBelow.remove(j);
					j--;
				}
			}
		}

		// chop segments so they cover the area under the cell
		for(int i=1; i<segsBelow.size(); i++)
		{
			LineSeg thisSeg = segsBelow.get(i);
			for(int j=0; j<i; j++)
			{
				LineSeg prevSeg = segsBelow.get(j);

				// eliminate entire segment if previous one covers all of it
				if (prevSeg.getMinX() <= thisSeg.getMinX() && prevSeg.getMaxX() >= thisSeg.getMaxX())
				{
					segsBelow.remove(i);
					i--;
					break;
				}

				// split segment if previous is in the middle
				if (prevSeg.getMaxX() < thisSeg.getMaxX() && prevSeg.getMinX() > thisSeg.getMinX())
				{
					double lowX1 = thisSeg.getMinX();
					double highX1 = prevSeg.getMinX();
					double lowX2 = prevSeg.getMaxX();
					double highX2 = thisSeg.getMaxX();
					thisSeg.x1 = lowX1;   thisSeg.x2 = highX1;
					segsBelow.add(i+1, new LineSeg(lowX2, thisSeg.y2, highX2, thisSeg.y2));
					continue;
				}

				// shorten segment if previous one covers part of it
				if (prevSeg.getMinX() <= thisSeg.getMinX() && prevSeg.getMaxX() > thisSeg.getMinX())
				{
					// previous one covers left side
					double lowX = prevSeg.getMaxX();
					double highX = thisSeg.getMaxX();
					thisSeg.x1 = lowX;   thisSeg.x2 = highX;
					continue;
				}
				if (prevSeg.getMaxX() >= thisSeg.getMaxX() && prevSeg.getMinX() < thisSeg.getMaxX())
				{
					// previous one covers right side
					double lowX = thisSeg.getMinX();
					double highX = prevSeg.getMinX();
					thisSeg.x1 = lowX;   thisSeg.x2 = highX;
					continue;
				}
			}
		}

		// remove lines that abut the cell; extend lines at the ends of the cell
		for(int i=0; i<segsBelow.size(); i++)
		{
			LineSeg ls = segsBelow.get(i);
			if (ls.y1 == segY)
			{
				segsBelow.remove(i);
				i--;
				continue;
			}
			if (ls.getMinX() >= segHX || ls.getMaxX() <= segLX)
			{
				segsBelow.remove(i);
				i--;
				continue;
			}
			if (ls.getMinX() <= segLX) { ls.x2 = ls.getMaxX(); ls.x1 = bound.getMinX(); }  
			if (ls.getMaxX() >= segHX) { ls.x1 = ls.getMinX(); ls.x2 = bound.getMaxX(); }  
		}

		// build rectangles
		for(LineSeg ls : segsBelow)
		{
			double lowX = ls.getMinX();
			double lowY = ls.getMinY();
			double highX = ls.getMaxX();
			double highY = segY;

			// clip rectangles that extend beyond the bounds of the cell
			if (lowX < segLX)
			{
				for(ERectangle r : rectangles)
				{
					if (r.getMinY() >= highY || r.getMaxY() <= lowY) continue;
					if (r.getMinX() >= highX || r.getMaxX() <= lowX) continue;
					if (r.getMinX() < segLX && r.getMaxX() > lowX) lowX = r.getMaxX();
				}
			}
			if (highX > segHX)
			{
				for(ERectangle r : rectangles)
				{
					if (r.getMinY() >= highY || r.getMaxY() <= lowY) continue;
					if (r.getMinX() >= highX || r.getMaxX() <= lowX) continue;
					if (r.getMaxX() > segHX && r.getMinX() < highX) highX = r.getMinX();
				}
			}
			emptyRects.add(ERectangle.fromLambda(lowX, lowY, highX-lowX, highY-lowY));
		}
	}

	/**
	 * Method to find all empty rectangles to the left a line segment.
	 * @param left the LineSeg to examine.
	 * @param rect the ERectangle that this LineSeg comes from (may be null for the right edge).
	 * @param rectangles a list of full ERectangles.
	 * @param bound the boundary of the entire Cell (limits the empty space).
	 * @param emptyRects the list of empty rectangles to build up.
	 */
	private void buildEmptyRectsLeft(LineSeg left, ERectangle rect, List<ERectangle> rectangles, ERectangle bound, List<ERectangle> emptyRects)
	{
		double segLY = left.getMinY();
		double segHY = left.getMaxY();
		double segX = left.getMinX();
		List<LineSeg> segsLeft = getSegmentsLeft(left, rect, rectangles);
		segsLeft.add(new LineSeg(bound.getMinX(), bound.getMinY(), bound.getMinX(), bound.getMaxY()));
		Collections.sort(segsLeft, new SortLineSegsLeft());

		// merge adjoining segments
		for(int i=0; i<segsLeft.size(); i++)
		{
			for(int j=i+1; j<segsLeft.size(); j++)
			{
				LineSeg thisSeg = segsLeft.get(i);
				LineSeg nextSeg = segsLeft.get(j);
				if (thisSeg.x1 != nextSeg.x1) break;
				LineSeg adjoin = thisSeg.adjoins(nextSeg);
				if (adjoin != null)
				{
					segsLeft.set(i, adjoin);
					segsLeft.remove(j);
					j--;
				}
			}
		}

		// chop segments so they cover the area under the cell
		for(int i=1; i<segsLeft.size(); i++)
		{
			LineSeg thisSeg = segsLeft.get(i);
			for(int j=0; j<i; j++)
			{
				LineSeg prevSeg = segsLeft.get(j);

				// eliminate entire segment if previous one covers all of it
				if (prevSeg.getMinY() <= thisSeg.getMinY() && prevSeg.getMaxY() >= thisSeg.getMaxY())
				{
					segsLeft.remove(i);
					i--;
					break;
				}

				// split segment if previous is in the middle
				if (prevSeg.getMaxY() < thisSeg.getMaxY() && prevSeg.getMinY() > thisSeg.getMinY())
				{
					double lowY1 = thisSeg.getMinY();
					double highY1 = prevSeg.getMinY();
					double lowY2 = prevSeg.getMaxY();
					double highY2 = thisSeg.getMaxY();
					thisSeg.y1 = lowY1;   thisSeg.y2 = highY1;
					segsLeft.add(i+1, new LineSeg(thisSeg.x2, lowY2, thisSeg.x2, highY2));
					continue;
				}

				// shorten segment if previous one covers part of it
				if (prevSeg.getMinY() <= thisSeg.getMinY() && prevSeg.getMaxY() > thisSeg.getMinY())
				{
					// previous one covers left side
					double lowY = prevSeg.getMaxY();
					double highY = thisSeg.getMaxY();
					thisSeg.y1 = lowY;   thisSeg.y2 = highY;
					continue;
				}
				if (prevSeg.getMaxY() >= thisSeg.getMaxY() && prevSeg.getMinY() < thisSeg.getMaxY())
				{
					// previous one covers right side
					double lowY = thisSeg.getMinY();
					double highY = prevSeg.getMinY();
					thisSeg.y1 = lowY;   thisSeg.y2 = highY;
					continue;
				}
			}
		}

		// remove lines that abut the cell; extend lines at the ends of the  cell
		for(int i=0; i<segsLeft.size(); i++)
		{
			LineSeg ls = segsLeft.get(i);
			if (ls.x1 == segX)
			{
				segsLeft.remove(i);
				i--;
				continue;
			}
			if (ls.getMinY() >= segHY || ls.getMaxY() <= segLY)
			{
				segsLeft.remove(i);
				i--;
				continue;
			}
			if (ls.getMinY() <= segLY) { ls.y2 = ls.getMaxY(); ls.y1 = bound.getMinY(); }  
			if (ls.getMaxY() >= segHY) { ls.y1 = ls.getMinY(); ls.y2 = bound.getMaxY(); }  
		}

		// build rectangles
		for(LineSeg ls : segsLeft)
		{
			double lowY = ls.getMinY();
			double lowX = ls.getMinX();
			double highY = ls.getMaxY();
			double highX = segX;

			// clip rectangles that extend beyond the bounds of the cell
			if (lowY < segLY)
			{
				for(ERectangle r : rectangles)
				{
					if (r.getMinX() >= highX || r.getMaxX() <= lowX) continue;
					if (r.getMinY() >= highY || r.getMaxY() <= lowY) continue;
					if (r.getMinY() < segLY && r.getMaxY() > lowY) lowY = r.getMaxY();
				}
			}
			if (highY > segHY)
			{
				for(ERectangle r : rectangles)
				{
					if (r.getMinX() >= highX || r.getMaxX() <= lowX) continue;
					if (r.getMinY() >= highY || r.getMaxY() <= lowY) continue;
					if (r.getMaxY() > segHY && r.getMinY() < highY) highY = r.getMinY();
				}
			}
			emptyRects.add(ERectangle.fromLambda(lowX, lowY, highX-lowX, highY-lowY));
		}
	}

	/**
	 * Class for defining a line segment, with two end points.
	 */
	private static class LineSeg
	{
		double x1, y1, x2, y2;

		public LineSeg(double x1, double y1, double x2, double y2)
		{
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
		}

		public double getMinX() { return Math.min(x1, x2); }

		public double getMaxX() { return Math.max(x1, x2); }

		public double getMinY() { return Math.min(y1, y2); }

		public double getMaxY() { return Math.max(y1, y2); }

		/**
		 * Method to tell whether this line segment adjoins another one.
		 * If they adjoin, it returns the merged segment.
		 * This assumes that the two lines are parallel, such that it is only necessary to compare endpoints.
		 * @param other the other LineSeg
		 * @return the merged LineSeg (null if they do not adjoin).
		 */
		public LineSeg adjoins(LineSeg other)
		{
			if (x1 == other.x1 && y1 == other.y1) return new LineSeg(x2, y2, other.x2, other.y2);
			if (x1 == other.x2 && y1 == other.y2) return new LineSeg(other.x1, other.y1, x2, y2);
			if (x2 == other.x1 && y2 == other.y1) return new LineSeg(x1, y1, other.x2, other.y2);
			if (x2 == other.x2 && y2 == other.y2) return new LineSeg(x1, y1, other.x1, other.y1);
			return null;
		}
	}

	/**
	 * Comparator class for sorting LineSegs by Y order, downward
	 */
	private static class SortLineSegsDown implements Comparator<LineSeg>
	{
		public int compare(LineSeg ls1, LineSeg ls2)
		{
			if (ls1.y1 < ls2.y1) return 1;
			if (ls1.y1 > ls2.y1) return -1;
			if (ls1.x1 < ls2.x1) return 1;
			if (ls1.x1 > ls2.x1) return -1; 
			return 0;
		}
	}

	/**
	 * Comparator class for sorting LineSegs by X order, leftward
	 */
	private static class SortLineSegsLeft implements Comparator<LineSeg>
	{
		public int compare(LineSeg ls1, LineSeg ls2)
		{
			if (ls1.x1 < ls2.x1) return 1;
			if (ls1.x1 > ls2.x1) return -1;
			if (ls1.y1 < ls2.y1) return 1;
			if (ls1.y1 > ls2.y1) return -1; 
			return 0;
		}
	}

	/**
	 * Comparator class for sorting ERectangle by size.
	 */
	private static class SortRectsBySize implements Comparator<ERectangle>
	{
		public int compare(ERectangle r1, ERectangle r2)
		{
			double s1 = r1.getWidth() * r1.getHeight();
			double s2 = r2.getWidth() * r2.getHeight();
			if (s1 < s2) return 1;
			if (s1 > s2) return -1;
			return 0;
		}
	}

	private List<LineSeg> getSegmentsBelow(LineSeg ls, ERectangle rect, List<ERectangle> rectangles)
	{
		List<LineSeg> segs = new ArrayList<LineSeg>();
		for (ERectangle r : rectangles)
		{
			if (r == rect) continue;
			double lX = r.getMinX();
			double hX = r.getMaxX();
			double hY = r.getMaxY();
			if (hX <= ls.getMinX() || lX >= ls.getMaxX()) continue;
			if (hY > ls.getMinY()) continue;
			segs.add(new LineSeg(lX, hY, hX, hY));
		}
		return segs;
	}

	private List<LineSeg> getSegmentsLeft(LineSeg ls, ERectangle rect, List<ERectangle> rectangles)
	{
		List<LineSeg> segs = new ArrayList<LineSeg>();
		for (ERectangle r : rectangles)
		{
			if (r == rect) continue;
			double hX = r.getMaxX();
			double lY = r.getMinY();
			double hY = r.getMaxY();
			if (hY <= ls.getMinY() || lY >= ls.getMaxY()) continue;
			if (hX > ls.getMinX()) continue;
			segs.add(new LineSeg(hX, lY, hX, hY));
		}
		return segs;
	}
}
