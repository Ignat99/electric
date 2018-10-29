/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PartReport.java
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
package com.sun.electric.tool.ncc.result;

import com.sun.electric.tool.ncc.netlist.NccNameProxy.PartNameProxy;
import com.sun.electric.tool.Job;

/** Save Part information needed by the NCC GUI when reporting
 * mismatches to the user.
 */
public class PartReport extends NetObjReport {
	static final long serialVersionUID = 0;
	public interface PartReportable extends NetObjReportable {
		PartNameProxy getNameProxy();
		String typeString();
		boolean isMos();
		boolean isResistor();
		double getWidth();
		double getLength();
	}
	
	private final PartNameProxy nameProxy;
	private final String typeString;
	private boolean isMos, isResistor;
	private double width, length;
	private void checkLenWidValid() {
		Job.error(!isMos && !isResistor,
				        "PartReport has no width or length");
	}
	public PartReport(PartReportable p) {
		super(p);
		nameProxy = p.getNameProxy();
		typeString = p.typeString();
		isMos = p.isMos();
		isResistor = p.isResistor();
		if (isMos || isResistor) {
			width = p.getWidth();
			length = p.getLength();
		}
	}

	public PartNameProxy getNameProxy() {return nameProxy;}
	public boolean isMos() {return isMos;}
	public boolean isResistor() {return isResistor;}
	public double getWidth() {
		checkLenWidValid();
		return width;
	}
	public double getLength() {
		checkLenWidValid();
		return length;
	}
	public String getTypeString() {
		return typeString;
	}
}
