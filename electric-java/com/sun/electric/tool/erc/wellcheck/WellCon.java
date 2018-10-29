/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WellCon.java
 * Author: Felix Schmidt
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
package com.sun.electric.tool.erc.wellcheck;

import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitiveNode.Function;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

public class WellCon {

	private Rectangle2D bound;
	private Point2D center;
	private int netNum;
	private NetValues wellNum = null;
	private boolean onProperRail;
	private boolean onRail;
	private PrimitiveNode.Function fun;
	private NodeInst ni;
	private AtomicBoolean marked = new AtomicBoolean(false);

	public WellCon(Rectangle2D bound, int netNum, NetValues wellNum, boolean onProperRail, boolean onRail,
			Function fun, NodeInst ni) {
		super();
		this.bound = bound;
		this.center = new Point2D.Double(bound.getCenterX(), bound.getCenterY());
		this.netNum = netNum;
		this.wellNum = wellNum;
		this.onProperRail = onProperRail;
		this.onRail = onRail;
		this.fun = fun;
		this.ni = ni;
	}

	public Rectangle2D getBound() { return bound; }

	public Point2D getCenter() { return center; }

	public int getNetNum() { return netNum; }

	public NetValues getWellNum() { return wellNum; }

	public void setWellNum(NetValues wn) { wellNum = wn; }

	public boolean isOnProperRail() { return onProperRail; }

	public void setOnProperRail(boolean opr) { onProperRail = opr; }

	public boolean isOnRail() { return onRail; }

	public void setOnRail(boolean or) { onRail = or; }

	public PrimitiveNode.Function getFun() { return fun; }

	public NodeInst getNi() { return ni; }

	public AtomicBoolean getMarked() { return marked; }
	
	public static class WellConComparator implements Comparator<WellCon> {

		private WellCon base;

		public WellConComparator(WellCon base) {
			this.base = base;
		}

		public int compare(WellCon o1, WellCon o2) {
			Double o1Dist = new Double(o1.getCenter().distance(base.getCenter()));
			Double o2Dist = new Double(o2.getCenter().distance(base.getCenter()));
			return Double.compare(o1Dist, o2Dist);
		}

	}

}
