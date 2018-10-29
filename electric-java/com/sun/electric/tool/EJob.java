/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EJob.java
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
package com.sun.electric.tool;

import com.sun.electric.database.EObjectInputStream;
import com.sun.electric.database.EObjectOutputStream;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.tool.Job.Type;
import com.sun.electric.util.ElapseTimer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * Class to track Job serializing and execution.
 */
public class EJob {

    public enum State {
        /** waiting on client */                CLIENT_WAITING,
        /** waiting on server */                WAITING,
        /** running on server */                RUNNING,
        /** done on server */                   SERVER_DONE,
        /** done on client */                   CLIENT_DONE;
    };

    /*private*/ final static String WAITING_NOW = "waiting now";
    /*private*/ final static String ABORTING = "aborting";

    /** Client which is owner of the Job. */    Client client;
//    /** True if this Job was started by server */public boolean startedByServer;
    /** job key */                              Job.Key jobKey;
    /** type of job (change or examine) */      public final Type jobType;
    /** name of job */                          public final String jobName;

    public Snapshot oldSnapshot;
    public Snapshot newSnapshot;
    EditingPreferences editingPreferences;

    /** progress */                             /*private*/ String progress = null;
    byte[] serializedJob;
    public byte[] serializedResult;
    boolean doItOk;
    Job serverJob;
    public Job clientJob;
    State state;
    /** list of saved Highlights */             int savedHighlights = -1;
    /** Fields changed on server side. */       ArrayList<Field> changedFields;

    /** Creates a new instance of EJob */
    public EJob(Client connection, int jobId, Job.Type jobType, String jobName, byte[] bytes) {
        this.client = connection;
        jobKey = new Job.Key(connection, jobId, jobType != Job.Type.CLIENT_EXAMINE);
        this.jobType = jobType;
        this.jobName = jobName;
        state = State.WAITING;
        serializedJob = bytes;
    }

    EJob(Job job, Job.Type jobType, String jobName, EditingPreferences editingPreferences) {
        this.jobType = jobType;
        this.jobName = jobName;
        state = State.CLIENT_WAITING;
        serverJob = clientJob = job;
        this.editingPreferences = editingPreferences;
    }

    public Job getJob() { return clientJob != null ? clientJob : serverJob; }

    private ClassLoader getClassLoader() {
        Job theJob = getJob();
        return (theJob == null ? EJob.class : theJob.getClass()).getClassLoader();
    }
    
    public Job.Inform getInform() {
        boolean isChange = jobType == Type.CHANGE || jobType == Type.UNDO;
        String toString = jobName+" (waiting)";
        int finished = -1;
        return new Job.Inform(jobKey, isChange, toString, ElapseTimer.createInstance(), finished);
    }

    public boolean isExamine() {
        return jobType.isExamine();
    }

    public boolean startedByServer() {
        return jobKey.startedByServer();
    }

    Throwable serialize(EDatabase database) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream out = new EObjectOutputStream(byteStream, database);
            out.writeObject(clientJob);
            out.flush();
            serializedJob = byteStream.toByteArray();
            return null;
        } catch (Throwable e) {
//        	if (e instanceof NotSerializableException)
        	{
//        		NotSerializableException nse = (NotSerializableException)e;
        		System.out.println("ERROR: Job '" + jobName + "' cannot serialize parameter: " + e.getMessage());
        		System.out.println("------------- Begin serialize() Exception stack trace --------------\n");
    			e.printStackTrace();
        		System.out.println("------------- End serialize() Exception stack trace ----------------\n");
        	}
            return e;
        }
    }

    public Throwable deserializeToServer()
    {
        try
        {
            final ClassLoader classLoader = getClassLoader();
            EDatabase database = EDatabase.serverDatabase();
            ObjectInputStream in = new EObjectInputStream(new ByteArrayInputStream(serializedJob), database)
            {
                @Override
                public Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException
                {
                    return Class.forName(desc.getName(), false, classLoader);
                }
            };
            Job job = (Job)in.readObject();
            in.close();
            job.ejob = this;
            job.database = database;
            serverJob = job;
            return null;
        } catch (Throwable e)
        {
            return e;
        }
    }

    public Throwable deserializeToClient()
    {
        try
        {
            final ClassLoader classLoader = getClassLoader();
            EDatabase database = EDatabase.clientDatabase();
            ObjectInputStream in = new EObjectInputStream(new ByteArrayInputStream(serializedJob), database)
            {
                @Override
                public Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException
                {
                    return Class.forName(desc.getName(), false, classLoader);
                }
            };
            Job job = (Job)in.readObject();
            in.close();
            job.ejob = this;
            job.database = database;
            clientJob = job;
            return null;
        } catch (Throwable e)
        {
            return e;
        }
    }

    void serializeResult(EDatabase database) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream out = new EObjectOutputStream(byteStream, database);
            doItOk = true;
            out.writeObject(null); // No exception
            out.writeInt(changedFields.size());
            Job job = jobType == Job.Type.CLIENT_EXAMINE ? clientJob : serverJob;
            for (Field f: changedFields) {
                String fieldName = f.getName();
                Object value = f.get(job);
                out.writeUTF(fieldName);
                try {
                    out.writeObject(value);
                } catch (NotSerializableException e) {
                    System.out.println("ERROR: Job '" + jobName + "' cannot serialize returned field " +
                            fieldName + " = " + value + " : " + e.getMessage());
                    throw e;
                }
            }
            out.close();
            serializedResult = byteStream.toByteArray();
        } catch (Throwable e) {
            Job.logger.warn("serializeResult failure", e);
            serializeExceptionResult(e, database);
        }
    }

    void serializeExceptionResult(Throwable jobException, EDatabase database) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream out = new EObjectOutputStream(byteStream, database);
            jobException.getStackTrace();
                out.writeObject(jobException);
            out.writeInt(0);
            out.close();
            serializedResult = byteStream.toByteArray();
        } catch (Throwable e) {
            Job.logger.warn("serializeExceptionResul failure", e);
            serializedResult = new byte[0];
        }
    }


    public Throwable deserializeResult() {
        try {
//            Class jobClass = clientJob.getClass();
            ObjectInputStream in = new EObjectInputStream(new ByteArrayInputStream(serializedResult), EDatabase.clientDatabase());
            Throwable jobException = (Throwable)in.readObject();
            int numFields = in.readInt();
            for (int i = 0; i < numFields; i++) {
                String fieldName = in.readUTF();
                Object value = in.readObject();
                Field f = findField(fieldName);
                f.setAccessible(true);
                f.set(clientJob, value);
            }
            in.close();
            return jobException;
        } catch (Throwable e) {
            Job.logger.warn("deserializeResult failure", e);
            return e;
        }
    }

    /**
     * Method to remember that a field variable of the Job has been changed by the doIt() method.
     * @param fieldName the name of the variable that changed.
     */
    protected void fieldVariableChanged(String fieldName) {
        Field fld = findField(fieldName);
        fld.setAccessible(true);
        changedFields.add(fld);
    }

    private Field findField(String fieldName) {
        Class jobClass = getJob().getClass();
//        Field fld = null;
        while (jobClass != Job.class) {
            try {
                return jobClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                jobClass = jobClass.getSuperclass();
            }
        }
        return null;
    }
}
