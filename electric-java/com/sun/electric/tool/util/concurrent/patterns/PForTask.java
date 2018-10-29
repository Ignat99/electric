/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: PForTask.java
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
package com.sun.electric.tool.util.concurrent.patterns;

import com.sun.electric.tool.util.concurrent.utils.BlockedRange;

/**
 * 
 * Base task for parallel for
 * 
 */
public abstract class PForTask<T extends BlockedRange<T>> extends PTask implements Cloneable {

	protected T range;

	public PForTask(PJob job, T range) {
		super(job);
		this.range = range;
	}

	public PForTask() {
		super(null);
	}

	protected void setRange(T range) {
		this.range = range;
	}

	/**
	 * set current job
	 * 
	 * @param job
	 */
	public void setPJob(PJob job) {
		this.job = job;
	}

}
