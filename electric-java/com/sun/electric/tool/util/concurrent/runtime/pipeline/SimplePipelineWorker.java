/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SimplePipelineWorker.java
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
package com.sun.electric.tool.util.concurrent.runtime.pipeline;

import com.sun.electric.tool.util.concurrent.runtime.ThreadID;
import com.sun.electric.tool.util.concurrent.runtime.pipeline.PipelineRuntime.Stage;
import com.sun.electric.tool.util.concurrent.runtime.pipeline.PipelineRuntime.StageImpl;

/**
 * @author fs239085
 * 
 */
public class SimplePipelineWorker<Input, Output> extends PipelineWorkerStrategy {

	private Stage<Input, Output> myStage;
	private StageImpl<Input, Output> impl;

	public SimplePipelineWorker(Stage<Input, Output> stage, StageImpl<Input, Output> impl) {
		this.myStage = stage;
		this.impl = impl;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.sun.electric.tool.util.concurrent.runtime.pipeline.PipelineWorkerStrategy
	 * #execute()
	 */
	@Override
	public void execute() {
		ThreadID.get();
		this.executed = 0;
		while (!abort) {
			Input item = myStage.recv();
			if (item != null) {
				try {
					Output result = impl.execute(item);
					myStage.forward(result);
				} finally {
					this.executed++;
				}
			}
		}

	}
}
