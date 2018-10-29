/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WorkingThread.java
 *
 * Copyright (c) 2011, Static Free Software. All rights reserved.
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
package com.sun.electric.tool;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Environment;

/**
 * Working thread is launched from Job.doIt on a server side
 * Job must be either com.sun.electric.tool.Job.Type#SERVER_EXAMINE.
 * Database modifications are not allowed within WorkingThread
 */
public class WorkingThread extends EThread {
    private final Environment env;
    private final EditingPreferences editingPreferences;

    /**
     * This constructor must be called from Job.doIt method.
     * @param threadName Name of working thread
     * @param job launching Job
     */
    public WorkingThread(String threadName, Job job) {
        super(threadName);
        EThread ownerThread = (EThread) Thread.currentThread();
        assert ownerThread.isServerThread;
        assert job.ejob == ownerThread.ejob;
        assert job.ejob.jobKey.doItOnServer;
        assert job.ejob.jobType == Job.Type.SERVER_EXAMINE;
        userInterface = new ServerJobManager.UserInterfaceRedirect(ownerThread.ejob.jobKey);
        ejob = ownerThread.ejob;
        isServerThread = ownerThread.isServerThread;
        database = ownerThread.database;
        env = job.getEnvironment();
        editingPreferences = job.getEditingPreferences();
    }

    /**
     * Call this method at the beginning of #run method
     */
    public void initRun() {
        Environment.setThreadEnvironment(env);
        EditingPreferences.lowLevelSetThreadLocalEditingPreferences(editingPreferences);
    }
}
