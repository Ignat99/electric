/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Pipeline.java
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

import com.sun.electric.tool.util.concurrent.runtime.pipeline.PipelineRuntime;
import com.sun.electric.tool.util.concurrent.runtime.pipeline.PipelineRuntime.StageImpl;

/**
 * @author fs239085
 * 
 */
public class Pipeline<PipeIn, PipeOut> {

    private PipelineRuntime<PipeIn, PipeOut> pipeline;

    public Pipeline() {
        this.pipeline = new PipelineRuntime<PipeIn, PipeOut>();
    }

    public void input(PipeIn in) {
        this.pipeline.input(in);
    }

    public <Input, Output> void addStage(StageImpl<Input, Output> impl, int numOfWorkers) {
        this.pipeline.addStage(impl, numOfWorkers);
    }

    public static class PipelineJob<PipeIn, PipeOut> {
        
       // private AtomicInteger added = new AtomicInteger(0);
       // private AtomicInteger processed = new AtomicInteger(0);

    }

}
