/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SteinerTree.java
 * Written by: Steven Rubin.
 *
 * Copyright (c) 2012, Static Free Software. All rights reserved.
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
package com.sun.electric.database.topology;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.tool.Job;
import com.sun.electric.util.ElapseTimer;

import java.awt.geom.Point2D;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class for constructing a Steiner Tree.
 */
public class SteinerTree
{
	/** true to run both Steiner Tree methods and compare them */
	private static final boolean COMPAREMETHODS = false;
	private List<SteinerTreePortPair> pairs;

	/**
	 * Method to return the Steiner Tree.
	 * @return a List of SteinerTreePortPair objects that describes the Steiner Tree.
	 */
	public List<SteinerTreePortPair> getTreeBranches()
	{
		return pairs;
	}

	/**
	 * Interface to define a point in the Steiner Tree that needs to be organized properly.
	 * The PortInst class implements this.
	 */
	public interface SteinerTreePort
	{
		public EPoint getCenter();
	}

	/**
	 * Class that defines a branch of the Steiner Tree, with two SteinerTreePort objects.
	 */
	public static class SteinerTreePortPair implements Comparable<SteinerTreePortPair>
	{
		private SteinerTreePort p1, p2;
		private List<PortInst> spineTapPorts;

		public SteinerTreePortPair(SteinerTreePort p1, SteinerTreePort p2)
		{
			this.p1 = p1;
			this.p2 = p2;
			spineTapPorts = null;
		}

		public SteinerTreePort getPort1() { return p1; }

		public SteinerTreePort getPort2() { return p2; }

		/**
		 * Method to return a List of PortInst objects that should be
		 * inserted into this port pair, spine-style.
		 * @return a list of spine tap PortInst objects.
		 */
		public List<PortInst> getSpineTaps() { return spineTapPorts; }

		/**
		 * Method to add an intermediate PortInst that will be inserted, spine-style,
		 * between the two ports in the Steiner Tree.
		 * @param pi the tap port to insert.
		 */
		public void addTapPort(PortInst pi)
		{
			if (spineTapPorts == null) spineTapPorts = new ArrayList<PortInst>();
			spineTapPorts.add(pi);
		}

        @Override
		public int compareTo(SteinerTreePortPair other)
		{
			double dist1 = other.p1.getCenter().distance(other.p2.getCenter());
			double dist2 = p1.getCenter().distance(p2.getCenter());
			if (dist1 < dist2) return 1;
			if (dist1 > dist2) return -1;
			return 0;
		}
	}

    /**
	 * Constructor takes a list of
	 * @param portList a List of SteinerTreePort objects that are to be organized into a Steiner Tree.
	 * @param disableAdvancedCode true to ignore private, advanced code for this.
	 */
	public SteinerTree(List<SteinerTreePort> portList, boolean disableAdvancedCode)
	{
		// the final list of edges in the Steiner tree
		pairs = new ArrayList<>();
		List<SteinerTreePortPair> newPairs = new ArrayList<>();
		if (portList.size() < 2) return;

		// gather statistics
    	ElapseTimer timerNew = ElapseTimer.createInstance();
    	ElapseTimer timerOld = ElapseTimer.createInstance();

    	// advanced code for steiner trees (minimum spanning tree computation)
		if (!disableAdvancedCode && hasMST() && portList.size() > 2)
		{
			// run special MST code
			Point2D[] points = new Point2D[portList.size()];

			for(int i=0; i<portList.size(); i++)
			{
				SteinerTreePort stpThis = portList.get(i);
				points[i] = new Point2D.Double(stpThis.getCenter().getX(), stpThis.getCenter().getY());
			}

			timerNew.start();
			int[] result = null;
            try {
    			result = (int[])doMSTMethod.invoke(mstClass, new Object[] {points});
            } catch (Exception e) {
            	if (Job.getDebug())
            		e.printStackTrace();
                System.out.println("Error running the new Steiner Tree module (" + e.getClass() + ")");
            }

            if (result != null)
            {
				for(int i=0; i<result.length; i += 2)
				{
					SteinerTreePort lastPi = portList.get(result[i]);
					SteinerTreePort thisPi = portList.get(result[i+1]);
					SteinerTreePortPair pp = new SteinerTreePortPair(lastPi, thisPi);
					newPairs.add(pp);
				}
				Collections.sort(newPairs);
    			if (COMPAREMETHODS) timerNew.end(); else
    			{
    				pairs = newPairs;
    				return;
    			}
            }
		}

		// old method of optimizing steiner tree
    	timerOld.start();

		// a list of points that are already in the tree (initially contains the first point, a random choice)
		List<SteinerTreePort> seen = new ArrayList<>();
		seen.add(portList.get(0));

		// initial list of points to be added to the tree (excluding the first point)
		List<SteinerTreePort> remaining = new ArrayList<>();
		for(int i=1; i<portList.size(); i++) remaining.add(portList.get(i));

		// iteratively find the closest unassigned point to the tree
		while (remaining.size() > 0)
		{
			// find the closest to anything in the Steiner tree
			double bestDist = Double.MAX_VALUE;
			SteinerTreePort bestRem = null, bestSeen = null;
			for(SteinerTreePort piRem : remaining)
			{
				for(SteinerTreePort piSeen : seen)
				{
					double dist = piRem.getCenter().distance(piSeen.getCenter());
					if (dist < bestDist)
					{
						bestDist = dist;
						bestRem = piRem;
						bestSeen = piSeen;
					}
				}
			}
			if (bestRem != null)
			{
				SteinerTreePortPair pp = new SteinerTreePortPair(bestRem, bestSeen);
				pairs.add(pp);
				remaining.remove(bestRem);
				seen.add(bestRem);
			}
		}
		Collections.sort(pairs);

		// compare two methods
		if (COMPAREMETHODS)
		{
			timerOld.end();
			System.out.println("Steiner Tree computation on " + portList.size()
					+ " points: old code took: " + timerOld + ", new code took: "
					+ timerNew + " on newpoints " + newPairs.size());

			// compare with new code
			if (newPairs.size() > 0)
			{
				boolean fail = false;
				if (newPairs.size() != pairs.size())
				{
					System.out.println("ERROR: Old Steiner code produced "+pairs.size()+" segments but new Steiner code produced "+newPairs.size());
					fail = true;
				}
				Set<SteinerTreePortPair> seenInNew = new HashSet<SteinerTreePortPair>();
				Set<SteinerTreePortPair> seenInOld = new HashSet<SteinerTreePortPair>();
				for(int i=0; i<pairs.size(); i++)
				{
					SteinerTreePortPair pairOld = pairs.get(i);
					boolean found = false;
					for(int j=0; j<newPairs.size(); j++)
					{
						SteinerTreePortPair pairNew = newPairs.get(j);
						double oldX1 = pairOld.p1.getCenter().getX();
						double oldY1 = pairOld.p1.getCenter().getY();
						double oldX2 = pairOld.p2.getCenter().getX();
						double oldY2 = pairOld.p2.getCenter().getY();
						double newX1 = pairNew.p1.getCenter().getX();
						double newY1 = pairNew.p1.getCenter().getY();
						double newX2 = pairNew.p2.getCenter().getX();
						double newY2 = pairNew.p2.getCenter().getY();
						if (((oldX1 == newX1 && oldY1 == newY1) && (oldX2 == newX2 && oldY2 == newY2)) ||
							((oldX1 == newX2 && oldY1 == newY2) && (oldX2 == newX1 && oldY2 == newY1)))
						{
							seenInNew.add(pairNew);
							seenInOld.add(pairOld);
							found = true;
							break;
						}
					}
					if (!found) fail = true;
				}
				if (fail)
				{
					System.out.println("Results are different");
					System.out.println("Old Steiner code: ");
					for(int i=0; i<pairs.size(); i++)
					{
						SteinerTreePortPair pp = pairs.get(i);
						if (seenInOld.contains(pp)) System.out.print("    "); else
							System.out.print("->  ");
						System.out.println("("+TextUtils.formatDistance(pp.p1.getCenter().getX())+","+
							TextUtils.formatDistance(pp.p1.getCenter().getY())+") TO ("+TextUtils.formatDistance(pp.p2.getCenter().getX())+","+
								TextUtils.formatDistance(pp.p2.getCenter().getY())+"), distance is "+
									TextUtils.formatDistance(pp.p1.getCenter().distance(pp.p2.getCenter())));
					}
					System.out.println("New Steiner code: ");
					for(int i=0; i<newPairs.size(); i++)
					{
						SteinerTreePortPair pp = newPairs.get(i);
						if (seenInNew.contains(pp)) System.out.print("    "); else
							System.out.print("->  ");
						System.out.println("("+TextUtils.formatDistance(pp.p1.getCenter().getX())+","+
							TextUtils.formatDistance(pp.p1.getCenter().getY())+") TO ("+TextUtils.formatDistance(pp.p2.getCenter().getX())+","+
								TextUtils.formatDistance(pp.p2.getCenter().getY())+"), distance is "+
									TextUtils.formatDistance(pp.p1.getCenter().distance(pp.p2.getCenter())));
					}
				}
			}
		}
	}

	/****************************** EXTERNAL CODE FOR STEINER TREES ******************************/

	private static boolean mstChecked = false;
    private static Class<?> mstClass;
    private static Method doMSTMethod = null;

    /**
     * Method to tell whether Minimum Spanning Tree code is available.
     * @return true if the Minimum Spanning Tree code is available.
     */
    public static boolean hasMST()
    {
        if (!mstChecked)
        {
        	mstChecked = true;
            try {
            	mstClass = Class.forName("com.oracle.labs.mso.mst.MST");
            	//mstClass = Class.forName("com.oracle.labs.mso.minspantree.MinSpanTree");		// this fails regression
            	doMSTMethod = mstClass.getMethod("doMST", new Class[]{Point2D[].class});
            } catch (Exception e) {}
        }
        return doMSTMethod != null;
    }
}
