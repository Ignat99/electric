/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NetObjReport.java
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

import java.io.Serializable;

import com.sun.electric.tool.ncc.result.PartReport.PartReportable;
import com.sun.electric.tool.ncc.result.PortReport.PortReportable;
import com.sun.electric.tool.ncc.result.WireReport.WireReportable;
import com.sun.electric.tool.Job;

/* Save information needed by the NCC GUI to report mismatch 
 * information to the user. 
 */
public abstract class NetObjReport implements Serializable {
	public interface NetObjReportable {
		String instanceDescription();
		String fullDescription();
		String getName();
	}
	private final String instanceDescription;
	private final String fullDescription;
	private final String name;

	public NetObjReport(NetObjReportable n) {
		instanceDescription = n.instanceDescription();
		fullDescription = n.fullDescription();
		name = n.getName();
	}

	public String fullDescription() {return fullDescription;}
	public String instanceDescription() {return instanceDescription;}
	public String getName() {return name;}
	public static NetObjReport newNetObjReport(NetObjReportable no) {
		if (no instanceof PartReportable) return new PartReport((PartReportable)no);
		else if (no instanceof WireReportable) return new WireReport((WireReportable)no);
		else if (no instanceof PortReportable) return new PortReport((PortReportable)no);
		Job.error(true, "unrecognized NetObject");
		return null;
	}

}
