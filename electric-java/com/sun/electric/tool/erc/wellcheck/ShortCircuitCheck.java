/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ShortCircuitCheck.java
 * Written by: Felix Schmidt
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

import com.sun.electric.tool.erc.ERCWellCheck.StrategyParameter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ShortCircuitCheck implements WellCheckAnalysisStrategy {

	private StrategyParameter parameter;

	public ShortCircuitCheck(StrategyParameter parameter) {
		super();
		this.parameter = parameter;
	}

	public void execute() {
		Map<Integer, WellCon> wellContacts = new HashMap<Integer, WellCon>();
		Map<Integer, Set<Integer>> wellShorts = new HashMap<Integer, Set<Integer>>();

		System.out.println("Checking short circuits in " + parameter.getWellCons().size() + " well contacts");

		for (WellCon wc : parameter.getWellCons()) {
			Integer wellIndex = new Integer(wc.getWellNum().getIndex());
			WellCon other = wellContacts.get(wellIndex);
			if (other == null)
				wellContacts.put(wellIndex, wc);
			else {
				if (wc.getNetNum() != other.getNetNum() && wc.getNi() != other.getNi()) {
					Integer wcNetNum = new Integer(wc.getNetNum());
					Set<Integer> shortsInWC = wellShorts.get(wcNetNum);
					if (shortsInWC == null) {
						shortsInWC = new HashSet<Integer>();
						wellShorts.put(wcNetNum, shortsInWC);
					}

					Integer otherNetNum = new Integer(other.getNetNum());
					Set<Integer> shortsInOther = wellShorts.get(otherNetNum);
					if (shortsInOther == null) {
						shortsInOther = new HashSet<Integer>();
						wellShorts.put(otherNetNum, shortsInOther);
					}

					// give error if not seen before
					if (!shortsInWC.contains(otherNetNum)) {
                        parameter.logError("Short circuit between well contacts", wc, other);
						shortsInWC.add(otherNetNum);
						shortsInOther.add(wcNetNum);
					}
				}
			}
		}
	}

}
