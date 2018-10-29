/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WireQualityMetric.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.routing.metrics;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.routing.metrics.WireQualityMetric.QualityResults;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.GenMath;

/**
 * @author Felix Schmidt
 * Modified by Gilda Garreton
 * 
 */
public class WireQualityMetric extends RoutingMetric<QualityResults> {

	protected static Logger logger = LoggerFactory.getLogger(WireQualityMetric.class);
	protected ElapseTimer timer;
	private String name;
	protected boolean regression = false; // to print some info only in regression mode.
	private PrintStream printWriter = null; // to write to a file if needed
	//For average calculations
	protected double avgHpwlReal = 0, avgHpwlIdeal = 0;
	protected double totalWL = 0, avgWlDivHpwlReal = 0, avgWlDivHpwlIdeal = 0;
	// avgWlDivHpwl will allow to calculate how far is a routing from the L-shape solution
	protected int avgVias = 0;
	public int numberOfRoutedNets = 0; // total number of nets routed
	public int numberOfTotalNets = 0; // total number of nets to route
	public int numRoutedSegments = 0, numFailedSegments = 0, numFailedBatches = 0;
	
	protected HashMap<WLBucket, BucketInstance> wlMapIdeal = new HashMap<WLBucket, BucketInstance>();
	protected HashMap<WLBucket, BucketInstance> wlMapReal = new HashMap<WLBucket, BucketInstance>();
	
	protected HashMap<ZPBucket, BucketInstance> viasMap = new HashMap<ZPBucket, BucketInstance>();
	
	private class BucketInstance
	{
		// Number of net names will give the actual number
		private List<String> nets = new ArrayList<String>();

		@Override
		public String toString()
		{
			StringBuffer buffer = new StringBuffer(nets.size() + " ");
//			if (Job.getDebug())
//				buffer.append(nets.toString());
            return buffer.toString();
		}
	}
	
	// tracing other values - specific data to this project -> not available via WireQualityMetric
	// wl brackets
	public static enum WLBucket {
		WLSmaller110(0, 1.1), WL110_130(1.1, 1.30), WL130_160(1.30, 1.60), WL160AndLarger(1.60, Double.MAX_VALUE);
	
		private double minV, maxV;
		WLBucket(double min, double max) {minV = min; maxV = max; }
		@Override
		public String toString()
		{
			if (this == WLSmaller110) return "WL (< 1.1)";
			if (this == WL160AndLarger) return "WL (> 1.6)";
			return "WL (" + minV + ", " + maxV + ")";
		}
		
		// linear search -> could be improved with tree structure I suppose if more elements are required
		public static WLBucket findBucket(double value)
		{
			for (WLBucket wl : WLBucket.values())
			{
				if (DBMath.isLessThanOrEqualTo(wl.minV, value) && DBMath.isLessThanOrEqualTo(value, wl.maxV))
					return wl;
			}
			return null;
		}
	}
	
	public static enum ZPBucket {
		ZPZero(0, 0), ZPUntil2(1, 2), ZPUntil4(3, 4), ZPUntil6(5, 6), ZPMoreThan6(6, Integer.MAX_VALUE);
		
		private int minV, maxV;
		ZPBucket(int min, int max) {minV = min; maxV = max; }
		@Override
		public String toString()
		{
			if (this == ZPMoreThan6) return "Via >+6";
			return "Via +" + maxV;
		}
		
		// linear search -> could be improved with tree structure I suppose if more elements are required
		public static ZPBucket findBucket(int value)
		{
			for (ZPBucket wl : ZPBucket.values())
			{
				if (DBMath.isLessThanOrEqualTo(wl.minV, value) && DBMath.isLessThanOrEqualTo(value, wl.maxV))
					return wl;
			}
			return null;
		}
	}
	
	public WireQualityMetric() {
		name = getClass().getName();
	}
	public WireQualityMetric(String s, ElapseTimer t) 
	{
		name = s; timer = t; regression = true;
	}
	
	public void addViaZeroBucket(int val, String netName)
	{
		ZPBucket zb = ZPBucket.findBucket(val);

		assert(zb != null);
		
		BucketInstance b = viasMap.get(zb);
		if (b == null) // first time
		{
			b = new BucketInstance();
			viasMap.put(zb, b);
		}
		b.nets.add(netName);	
	}
	
	public void addWLLengthToBucket(double val, String netName, boolean real)
	{
		WLBucket wlb = WLBucket.findBucket(val);
		assert(wlb != null);
		
		BucketInstance b = (real)? wlMapReal.get(wlb) : wlMapIdeal.get(wlb);
		if (b == null) // first time
		{
			b = new BucketInstance();
			if (real)
				wlMapReal.put(wlb, b);
			else
				wlMapIdeal.put(wlb, b);
		}
		b.nets.add(netName);	
	}
	
	public double getTotalWireLength() {return totalWL;}
	public String getName() {return name;}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.routing.metrics.RoutingMetric#calculate(com.sun
	 * .electric.database.hierarchy.Cell)
	 */
	@Override
	public QualityResults calculate(Cell cell) {
		QualityResults result = null;
		
		try {
			result = startLogging(cell.getName());
			
	        logger.trace("calculate wire length");
	        // assumption is that all wires are routing wires
	        result.wireLength = new WireLengthMetric().calculate(cell);
	        logger.debug("wire length metric: " + result.wireLength);
	
	        logger.trace("calculate unrouted nets");
	        result.unroutedSegments = new UnroutedNetsMetric().calculate(cell);
	        logger.debug("unrouted nets metric: " + result.unroutedSegments);
	
			logger.trace("calculate via amount metric...");
			result.vias = new ViaAmountMetric().calculate(cell);
			logger.debug("via amount metric: " + result.vias);
	
			logger.trace("calculate stacked via amount metric...");
			result.stackedVias = new StackedViasAmountMetric().calculate(cell);
			logger.debug("stacked via amount metric: " + result.stackedVias);
	
			logger.trace("calculate detouring amount metric...");
			result.detourings = new DetouringAmountMetric().calculate(cell);
			logger.debug("detouring amount metric: " + result.detourings);
	
			logger.trace("calculate evenness metric...");
			result.evenness = new EvennessMetric().calculate(cell);
			logger.debug("evenness metric: " + result.evenness);
	
			logger.debug("============================");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
	protected void info(String data)
	{
		if (printWriter != null)
			printWriter.println(data);
		if (Job.getDebug())
			logger.info(data);
	}
	
	public void setOutput(PrintStream out)
	{
		printWriter = out;
	}
	
	public String printAverageResults()
	{
		if (numberOfRoutedNets == 0) // no results
		{
			info("Error: no results found");
			return "";
		}			
		StringBuffer buff = new StringBuffer();
		buff.append("Routing Statistics for '" + name + "':\n");
		
		info("XXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		BigDecimal reg = new BigDecimal(GenMath.toNearest(totalWL, 0.1)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
		
		String temp = "\tTotal wire length " + TextUtils.formatDistance(reg.doubleValue()) + "\n";
		buff.append(temp); info(temp);
		
		// Different Real WL buckets
		for (WLBucket wlb : WLBucket.values())
		{
			BucketInstance mi = wlMapReal.get(wlb);
			String val = "\tReal" + wlb + " = " + ((mi!=null)?mi:0) + "\n";
			buff.append(val); info(val);
		}
		// Different Ideal WL buckets
		for (WLBucket wlb : WLBucket.values())
		{
			BucketInstance mi = wlMapIdeal.get(wlb);
			String val = "\tIdeal" + wlb + " = " + ((mi!=null)?mi:0) + "\n";
			buff.append(val); info(val);
		}
		
		reg = new BigDecimal(GenMath.toNearest(avgVias/numberOfRoutedNets, 0.1)).setScale(1, BigDecimal.ROUND_HALF_EVEN);
		temp = "\tAverage number of vias " + reg.doubleValue() + "\t";
		buff.append(temp); info(temp);
		reg = new BigDecimal(GenMath.toNearest(avgVias/numRoutedSegments, 0.1)).setScale(1, BigDecimal.ROUND_HALF_EVEN);
		temp = "\tAverage number of vias based on routed segments " + reg.doubleValue() + "\n";
		buff.append(temp); info(temp);
		// Different Ideal WL buckets
		for (ZPBucket wlb : ZPBucket.values())
		{
			BucketInstance mi = viasMap.get(wlb);
			String val = "\t" + wlb + " = " + ((mi!=null)?mi:0) + "\n";
			buff.append(val); info(val);
		}

		reg = new BigDecimal(GenMath.toNearest(avgHpwlReal/numberOfRoutedNets, 0.01)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
		temp = "\tAverage Real HPWL " + TextUtils.formatDistance(reg.doubleValue()) + "\t";
		buff.append(temp); info(temp);
		reg = new BigDecimal(GenMath.toNearest(avgHpwlIdeal/numberOfRoutedNets, 0.01)).setScale(2, BigDecimal.ROUND_HALF_EVEN);
		temp = "\tAverage Ideal HPWL " + TextUtils.formatDistance(reg.doubleValue()) + "\n";
		buff.append(temp); info(temp);

		reg = new BigDecimal(GenMath.toNearest(avgWlDivHpwlReal/numberOfRoutedNets, 0.001)).setScale(3, BigDecimal.ROUND_HALF_EVEN);
		temp = "\tAverage Real WL v/s HPWL " + reg.doubleValue() + "\t";
		buff.append(temp); info(temp);
		reg = new BigDecimal(GenMath.toNearest(avgWlDivHpwlIdeal/numberOfRoutedNets, 0.001)).setScale(3, BigDecimal.ROUND_HALF_EVEN);
		temp = "\tAverage Ideal WL v/s HPWL " + reg.doubleValue();
		buff.append(temp); info(temp);
		info("\nXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
		
		return buff.toString();
	}
	
	/**
	 * Method to initialize quality measurements including
	 * machine information and instance to store quality values
	 * @param name net or cell name associated with results.
	 * @return the quality results.
	 * @throws Exception UnknownHostException in case machine address can't be open
	 */
	protected QualityResults startLogging(String name) throws UnknownHostException
	{
		InetAddress addr = InetAddress.getLocalHost();
		String hostname = addr.getHostName();
		Date now = new Date();
			
		info("============================");
		
		// print machine/version/date only when it is used in regression mode
		if (regression)
		{
			logger.debug("metric name: " + name);
			logger.debug("machine: " + hostname);
			logger.debug("date: " + TextUtils.formatDate(now));
			logger.debug("Electric's version: " + Version.getVersion());
		}
		if (timer != null)
			logger.debug("execution time: " + timer);
		
		return new QualityResults(name);
	}
	
	/**
	 * Method to calculate net quality
	 * @param net Network to analyze
	 */
	public QualityResults calculate(Network net)
	{
		QualityResults result = null;
		
		numberOfTotalNets++;
		numberOfRoutedNets++;
		
		try {
			result = startLogging(net.getName());
			
	        //logger.trace("calculate wire length");
	        // assumption is that all wires are routing wires
//	        result.wireLength = new Double(0);
	        totalWL += result.wireLength = new WireLengthMetric().reduce(result.wireLength, net);
	        info("wire length metric for net '" + net.getName() + "': " + result.wireLength);
	
//	        logger.trace("calculate unrouted nets");
//	        result.unroutedSegments = new UnroutedNetsMetric().calculate(cell);
//	        logger.debug("unrouted nets metric: " + result.unroutedSegments);
//	
			//logger.trace("calculate via amount metric...");
	        avgVias += result.vias = new ViaAmountMetric().calculate(net);
			info("via amount metric for net '" + net.getName() + "': " + result.vias);
			
			//logger.trace("calculate HPWL amount metric...");
			double val = new HalfPerimeterWireLengthMetric().calculate(net);
			result.addSegmentHPWL(val, true);
			avgHpwlReal += val;
			info("Real HPWL amount metric for net '" + net.getName() + "': " + val);

			val = result.getWLDivHPWL(true);
			avgWlDivHpwlReal += val;
			info("Real WL v/s HPWL for net '" + net.getName() + "': " + val);
			
//			logger.trace("calculate stacked via amount metric...");
//			result.stackedVias = new StackedViasAmountMetric().calculate(cell);
//			logger.debug("stacked via amount metric: " + result.stackedVias);
//	
//			logger.trace("calculate detouring amount metric...");
//			result.detourings = new DetouringAmountMetric().calculate(cell);
//			logger.debug("detouring amount metric: " + result.detourings);
//	
//			logger.trace("calculate evenness metric...");
//			result.evenness = new EvennessMetric().calculate(cell);
//			logger.debug("evenness metric: " + result.evenness);
	
			info("============================");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	public static class QualityResults {
		public Integer vias;
		public Integer stackedVias;
		public Integer detourings;
		public Double evenness;
        public Double wireLength;
        public Integer unroutedSegments;
        public String resultName;
        private List<Double> segmentIdealHPWL = new ArrayList<Double>(); // list to store ideal hpwl
        private List<Double> segmentRealHPWL = new ArrayList<Double>(); // list to store real hpwl
        private List<Integer> segmentTotalVias = new ArrayList<Integer>();
        private List<Integer> segmentZeroVias = new ArrayList<Integer>();
        
        // forcing call from startLogging from another packages
        private QualityResults(String name) { resultName = name;}
       
        public int numOfSegments(boolean real) { return (real)? segmentRealHPWL.size() : segmentIdealHPWL.size(); }
        
        public void addSegmentViaValues(int totalVias, int zeroValue)
        {
        	segmentTotalVias.add(totalVias);
        	segmentZeroVias.add(zeroValue);
        }
        
        public int getSegmentViaValue()
        {
        	int total = 0;
        	for (int i = 0; i < segmentTotalVias.size(); i++)
        	{
        		total = segmentTotalVias.get(i) - segmentZeroVias.get(i);
        	}
        	return total;
        }
        
        public void addSegmentHPWL(double val, boolean real)
        {
        	if (real)
        		segmentRealHPWL.add(val);
        	else
        		segmentIdealHPWL.add(val);
        }
        
        public double getSegmentHPWL(boolean avg, boolean real)
        {
        	double val = 0;
        	List<Double> segmentHPWL = (real) ? segmentRealHPWL : segmentIdealHPWL;
        	int num = segmentHPWL.size();
        	
        	if (num == 0) return val;
        	
        	for (int i = 0; i < segmentHPWL.size(); i++)
        		val += segmentHPWL.get(i);
        	return (avg)? val/num : val;
        }
        
        public double getWLDivHPWL(boolean real)
        {
        	List<Double> segmentHPWL = (real) ? segmentRealHPWL : segmentIdealHPWL;
        	assert(segmentHPWL.size() > 0 && wireLength != null);
        	double val = wireLength/getSegmentHPWL(true, real);
        	if (GenMath.doublesLessThan(val, 1))
        		System.out.println("Check WL v/s HPWL calculation in '" + resultName + "' is less than 1:" + val);
        	return val;
        }
	}

	/* (non-Javadoc)
	 * @see com.sun.electric.tool.routing.metrics.RoutingMetric#reduce(java.lang.Object, com.sun.electric.database.topology.ArcInst)
	 */
	@Override
	protected QualityResults reduce(QualityResults result, ArcInst instance, Network net) {
		// [fschmidt] method not required here
		throw new UnsupportedOperationException();
	}

}
