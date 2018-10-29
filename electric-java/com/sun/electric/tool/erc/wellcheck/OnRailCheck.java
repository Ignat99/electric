/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: OnRailCheck.java
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

import com.sun.electric.tool.Job;
import com.sun.electric.tool.erc.ERCWellCheck.StrategyParameter;
import com.sun.electric.tool.erc.ERCWellCheck.Transistor;
import com.sun.electric.util.CollectionFactory;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnRailCheck implements WellCheckAnalysisStrategy {

	private Set<Integer> networkExportAvailable;
	private Set<Integer> networkWithExportCache;
	private Map<Integer, List<Transistor>> transistors;
	private Set<Transistor> alreadyHit;
	private StrategyParameter parameter;

	public OnRailCheck(StrategyParameter parameter, Set<Integer> networkExportAvailable,
			Map<Integer, List<Transistor>> transistors) {
		super();
		this.networkExportAvailable = networkExportAvailable;
		this.transistors = transistors;
		this.networkWithExportCache = CollectionFactory.createHashSet();
		this.parameter = parameter;
	}

	public void execute() {
		int total = parameter.getWellCons().size();
		int count = 0;
		Job.getUserInterface().startProgressDialog("Checking rails on " + total + " well contacts", null);
		for (WellCon wc : parameter.getWellCons()) {
			if (!wc.isOnRail()) {
				if (networkExportAvailable.contains(wc.getNetNum()))
					wc.setOnRail(true);
				else if (networkWithExportCache.contains(wc.getNetNum())) {
					wc.setOnRail(true);
				} else {
					Set<Integer> startPath = new HashSet<Integer>();
					List<Transistor> startTrans = transistors.get(wc.getNetNum());
					if (startTrans != null) {
						if (createTransistorChain(startPath, startTrans.get(0), false)) {
							wc.setOnRail(true);
							networkWithExportCache.addAll(startPath);
						}
					}
				}
			}

			if (!(wc.isOnRail() || wc.isOnProperRail())) {
				if (Utils.canBeSubstrateTap(wc.getFun())) {
					if (parameter.getWellPrefs().mustConnectPWellToGround) {
                        parameter.logError("P-Well contact '" + wc.getNi().getName() + "' not connected to ground", wc);
                    }
				} else {
					if (parameter.getWellPrefs().mustConnectNWellToPower) {
                        parameter.logError("N-Well contact '" + wc.getNi().getName() + "' not connected to ground", wc);
                    }
				}
			}
			count++;
			if ((count%10) == 0) Job.getUserInterface().setProgressValue(count * 100 / total);
		}
		Job.getUserInterface().stopProgressDialog();
	}

	private boolean createTransistorChain(Set<Integer> path, Transistor node, boolean result) {
		alreadyHit = new HashSet<Transistor>();

		result |= createTransistorRec(path, node, result, node.getDrainNet().get());
		if (!result)
			result |= createTransistorRec(path, node, result, node.getSourceNet().get());

		return result;

	}

	private boolean createTransistorRec(Set<Integer> path, Transistor node, boolean result, int num) {
		List<Transistor> transis = new LinkedList<Transistor>();
		transis.add(node);
		alreadyHit.add(node);

		while (transis.size() > 0) {
			Transistor transistor = transis.get(0);

			transis.remove(transistor);

			Integer neighbor = null;

			if (transistor.getDrainNet().get() == num) {
				neighbor = transistor.getSourceNet().get();
			} else {
				neighbor = transistor.getDrainNet().get();
			}

			path.add(neighbor);
			num = neighbor;

			result |= networkExportAvailable.contains(neighbor);

			if (!result) {
				result |= networkWithExportCache.contains(neighbor);
			}

			// cut the line
			if (result)
				return result;

			for (Transistor trans : transistors.get(neighbor)) {
				if (!alreadyHit.contains(trans)) {
					transis.add(trans);
					alreadyHit.add(trans);
				}
			}

		}

		return result;
	}

}
