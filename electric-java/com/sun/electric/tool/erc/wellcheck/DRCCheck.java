/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DRCCheck.java
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

import com.sun.electric.database.geometry.PolyBase;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.technology.DRCTemplate;
import com.sun.electric.technology.Layer;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.erc.ERCWellCheck.StrategyParameter;
import com.sun.electric.tool.erc.ERCWellCheck.WellBound;
import com.sun.electric.util.ElapseTimer;

import java.awt.geom.Rectangle2D;
import java.util.Iterator;

public class DRCCheck implements WellCheckAnalysisStrategy {

	private Layer pWellLayer;
	private Layer nWellLayer;
	private RTNode<WellBound> pWellRoot;
	private RTNode<WellBound> nWellRoot;
	private StrategyParameter parameters;

	public DRCCheck(StrategyParameter parameters, Layer pWellLayer, Layer nWellLayer, RTNode<WellBound> pWellRoot,
			RTNode<WellBound> nWellRoot) {
		super();
		this.parameters = parameters;
		this.pWellLayer = pWellLayer;
		this.nWellLayer = nWellLayer;
		this.pWellRoot = pWellRoot;
		this.nWellRoot = nWellRoot;
	}

	public void execute() {
		if (parameters.getWellPrefs().drcCheck) {
			ElapseTimer timer = ElapseTimer.createInstance();
			timer.start();
			DRCTemplate pRule = DRC.getSpacingRule(pWellLayer, null, pWellLayer, null, false, -1, 0, 0);
			DRCTemplate nRule = DRC.getSpacingRule(nWellLayer, null, nWellLayer, null, false, -1, 0, 0);
			if (pRule != null)
				findDRCViolations(pWellRoot, pRule.getValue(0));
			if (nRule != null)
				findDRCViolations(nWellRoot, nRule.getValue(0));

			timer.end();
			System.out.println("   Design rule check took " + timer.toString());
		}

	}

	private void findDRCViolations(RTNode<WellBound> rtree, double minDist) {
		for (int j = 0; j < rtree.getTotal(); j++) {
			if (rtree.getFlag()) {
				WellBound child = rtree.getChildLeaf(j);
				if (child.getNetID() == null)
					continue;

				// look all around this geometry for others in the well area
				Rectangle2D searchArea = new Rectangle2D.Double(child.getBounds().getMinX() - minDist, child
						.getBounds().getMinY()
						- minDist, child.getBounds().getWidth() + minDist * 2, child.getBounds().getHeight()
						+ minDist * 2);
				for (Iterator<WellBound> sea = new RTNode.Search<WellBound>(searchArea, rtree, true); sea.hasNext();) {
					WellBound other = sea.next();
					if (other.getNetID().getIndex() <= child.getNetID().getIndex())
						continue;
					if (child.getBounds().getMinX() > other.getBounds().getMaxX() + minDist
							|| other.getBounds().getMinX() > child.getBounds().getMaxX() + minDist
							|| child.getBounds().getMinY() > other.getBounds().getMaxY() + minDist
							|| other.getBounds().getMinY() > child.getBounds().getMaxY() + minDist)
						continue;

					PolyBase pb = new PolyBase(child.getBounds());
					double trueDist = pb.polyDistance(other.getBounds());
					if (trueDist < minDist) {
						parameters.logError(
								"Well areas too close (are " + TextUtils.formatDistance(trueDist)
										+ " but should be " + TextUtils.formatDistance(minDist) + " apart)",
										child, other);
					}
				}
			} else {
				RTNode<WellBound> child = rtree.getChildTree(j);
				findDRCViolations(child, minDist);
			}
		}
	}

}
