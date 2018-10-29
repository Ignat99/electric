/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Track.java
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
package com.sun.electric.tool.generator.flag.router;

import java.util.TreeSet;

import com.sun.electric.tool.Job;

public class Track {
	/** X or Y coordinate of center of track */
	private final double center;
	private final int trackNdx;
	private final Channel channel;
	private TreeSet<Segment> segments = new TreeSet<Segment>();
	
	public Track(double coord, Channel channel, int trackNdx) {
		this.center = coord;
		this.trackNdx=trackNdx; 
		this.channel=channel;
	}
	
	// does a blockage overlap the interval?
	public boolean isAvailable(double min, double max) {
		for (Segment s : segments) {
			if (s.min>max) return true;
			if (s.max<min) continue;
			return false;
		}
		return true;
	}
	
	private void sanityCheck() {
		Segment prev = null;
		for (Segment s : segments) {
			if (prev!=null) {
				Job.error(prev.min>=s.min, "illegally ordered segments");
				Job.error(prev.max>s.min, "overlapping segments");
			}
			prev = s;
		}
	}
	
	public Segment allocate(double min, double max) {
		// sanity check
		Job.error(!isAvailable(min, max), "overlapping blockages");
		Segment seg = new Segment(min, max, this, trackNdx);
		segments.add(seg);
		sanityCheck();
		return seg;
	}
	/** Allocate longest segment within the interval [min, max] that
	 * covers src. */
	public Segment allocateBiggest(double min, double src, double max) {
		Segment prev = null;
		Segment next = null;
		for (Segment s : segments) {
			if (s.max<=src)  prev=s;
			// if segment already covers src then all is lost
			if (s.min<=src && src<=s.max) return null;
			if (src<=s.min) {next=s; break;}
		}
		if (next!=null) max = Math.min(max, next.min);
		if (prev!=null) min = Math.max(min, prev.max);
		return new Segment(min, max, this, trackNdx);
	}
	public boolean isHorizontal() {return channel.isHorizontal();}
	public double getCenter() {return center;}
	public int getIndex() {return trackNdx;}
	public void resort(Segment s) {
		segments.remove(s);
		segments.add(s);
	}
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("    Track center: "+center+"\n");
		if (segments.size()!=0) {
			sb.append("      ");
			for (Segment s : segments)  sb.append(s.toString()+"  ");
			sb.append("\n");
		}
		return sb.toString();
	}
}
