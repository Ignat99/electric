/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SoGWireQualityMetric.java
 *
 * Copyright (c) 2015, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.routing.seaOfGates;

import java.net.UnknownHostException;

import com.sun.electric.tool.Job;
import com.sun.electric.tool.routing.metrics.WireQualityMetric;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.NeededRoute;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.RouteBatch;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.SearchVertex;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.Wavefront;

public class SoGWireQualityMetric extends WireQualityMetric
{
	public SoGWireQualityMetric(String s)
	{
		super(s, null);
	}

	/**
	 * Method to calculate net quality
	 * @param batch batch of routes to examine.
	 */
	public QualityResults calculate(RouteBatch batch)
	{
		QualityResults result = null;
		boolean atLeastOneSegment = false;

		numberOfTotalNets++;

		try {
			result = startLogging(batch.netName);

			result.wireLength = new Double(0);
			result.vias = new Integer(0);
			double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
			double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

			for(NeededRoute nr : batch.routesInBatch)
			{
				if (nr.getRoutedSucess())
				{
					numRoutedSegments++;

					Wavefront winningWF = nr.getWavefront();

					if (winningWF.vertices.size() < 2)
					{
						System.out.println("WARNING: Review net '" + batch.netName + "' - it has less than 2 vertices");
						continue;
					}
					// ideal HPWL from start and end points
					SearchVertex theFirst = winningWF.vertices.get(0);
					SearchVertex theLast = winningWF.vertices.get(winningWF.vertices.size()-1);
					double theSegMinX = Math.min(theFirst.getX(), theLast.getX());
					double theSegMaxX = Math.max(theFirst.getX(), theLast.getX());
					double theSegMinY = Math.min(theFirst.getY(), theLast.getY());
					double theSegMaxY = Math.max(theFirst.getY(), theLast.getY());
					result.addSegmentHPWL((theSegMaxX - theSegMinX) + (theSegMaxY - theSegMinY), false);
					int startZ = theFirst.getZ();
					int endZ = theLast.getZ();
					int maxZ = Math.max(startZ, endZ);
					int countVias = 0;

					// TODO: gather statistics for routeBatches[b].netName
					for (int i=1; i<winningWF.vertices.size(); i++)
					{
						SearchVertex svLast = winningWF.vertices.get(i-1);
						/// Collecting data for HPWL
						if (i == 1)
						{
							minX = Math.min(minX, svLast.getX());
							minY = Math.min(minY, svLast.getY());
							maxX = Math.max(maxX, svLast.getX());
							maxY = Math.max(maxY, svLast.getY());
						}

						SearchVertex sv = winningWF.vertices.get(i);
						if (svLast.getZ() != sv.getZ())
						{
							// changed layer, count via
//							result.vias++;
							countVias++;
							maxZ = Math.max(maxZ, sv.getZ());
						} else
						{
							/// Collecting data for HPWL
							minX = Math.min(minX, sv.getX());
							minY = Math.min(minY, sv.getY());
							maxX = Math.max(maxX, sv.getX());
							maxY = Math.max(maxY, sv.getY());
							// ran wire
							double dX = Math.abs(svLast.getX() - sv.getX());
							double dY = Math.abs(svLast.getY() - sv.getY());
							result.wireLength += Math.sqrt(dY*dY + dX*dX);
						}
					}
					result.vias += countVias;
					result.addSegmentViaValues(countVias, (maxZ-startZ) + (maxZ-endZ)); // zero value
					atLeastOneSegment = true;
				} else
				{
					if (nr.isAlreadyRouted())
					{
						numRoutedSegments++;
					} else
					{
						if (nr.getErrorMessage() != null)
						{
							numFailedSegments++;
						}
					}
				}
			}

			// Only when routing was found
			double avgWLHPWLReal = 0, avgWLHPWLIdeal = 0;
			if (atLeastOneSegment)
			{
				result.addSegmentHPWL((maxX - minX) + (maxY - minY), true); //.hpwlOLD = (maxX - minX) + (maxY - minY);
				avgHpwlReal += result.getSegmentHPWL(false, true);
				avgHpwlIdeal += result.getSegmentHPWL(false, false);
		        totalWL += result.wireLength;
		        avgVias += result.vias;
		        avgWLHPWLReal = result.getWLDivHPWL(true);
		        avgWLHPWLIdeal = result.getWLDivHPWL(false);
		        addWLLengthToBucket(avgWLHPWLReal, result.resultName, true);
		        addWLLengthToBucket(avgWLHPWLIdeal, result.resultName, false);
		        addViaZeroBucket(result.getSegmentViaValue(), result.resultName);
				avgWlDivHpwlReal += avgWLHPWLReal;
				avgWlDivHpwlIdeal += avgWLHPWLIdeal;
				numberOfRoutedNets++;
			}
			else
				numFailedBatches++;

	        //logger.trace("calculate wire length");
	        info("wire length metric for net '" + batch.netName + "': " + result.wireLength);

//	        logger.trace("calculate unrouted nets");
//	        result.unroutedSegments = new UnroutedNetsMetric().calculate(cell);
//	        logger.debug("unrouted nets metric: " + result.unroutedSegments);
//
			//logger.trace("calculate via amount metric...");
			info("via amount metric for net '" + batch.netName + "': " + result.vias);
			info("routed segment metric for net '" + batch.netName + "': " + result.numOfSegments(true));
			info("via++ metric for net '" + batch.netName + "': " + result.getSegmentViaValue());

			//logger.trace("calculate HPWL amount metric...");
			info("Real HPWL amount metric for net '" + batch.netName + "': " + result.getSegmentHPWL(true, true));
			info("Ideal HPWL amount metric for net '" + batch.netName + "': " + result.getSegmentHPWL(true, false));

			info("Real WL v/s HPWL for net '" + batch.netName + "': " + avgWLHPWLReal);
			info("Ideal WL v/s HPWL for net '" + batch.netName + "': " + avgWLHPWLIdeal);

			info("============================");
		} catch (UnknownHostException e) {
			if (Job.getDebug())
				e.printStackTrace();
			else
				System.out.println("No name or service not known");
		}
		return result;
	}
}
