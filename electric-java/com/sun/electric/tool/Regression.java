/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Regression.java
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

import com.sun.electric.Main;
import com.sun.electric.database.EObjectOutputStream;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.id.IdReader;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.Setting;
import com.sun.electric.technology.TechFactory;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.lang.EvalJavaBsh;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collections;
import java.util.Map;

/**
 * Simple client for regressions.
 */
public class Regression {

    public static boolean runScript(Process process, String script) {
        Pref.forbidPreferences();
        IdReader reader = null;
        Snapshot currentSnapshot = IdManager.stdIdManager.getInitialSnapshot();
        System.out.println("Running " + script);

        try {
            InputStream inStream = process.getInputStream();
            OutputStream outStream = process.getOutputStream();
            InputStream errStream = process.getErrorStream();
            new ExecProcessReader(errStream).start();
            reader = new IdReader(new DataInputStream(new BufferedInputStream(inStream)), IdManager.stdIdManager);
            int protocolVersion = reader.readInt();
            if (protocolVersion != Job.PROTOCOL_VERSION) {
                System.out.println("Client's protocol version " + Job.PROTOCOL_VERSION + " is incompatible with Server's protocol version " + protocolVersion);
                for (int i = 0; i < 100; i++) {
                    System.out.print((char) reader.readByte());
                }
                System.out.println();
                return false;
            }
            int connectionId = reader.readInt();
            System.out.format("%1$tT.%1$tL ", Calendar.getInstance());
            System.out.println("Connected id=" + connectionId);

            DataOutputStream clientOutputStream = new DataOutputStream(new BufferedOutputStream(outStream));
            writeServerJobs(clientOutputStream, connectionId, script);
            clientOutputStream.close();

            int curJobId = -1;
            AbstractUserInterface ui = new Main.UserInterfaceDummy();
            ui.patchConnectionId(connectionId);
            boolean passed = true;
            for (;;) {
                byte tag = reader.readByte();
                long timeStamp = reader.readLong();
//                System.out.format("%1$tT.%1$tL->%2$tT.%2$tL %3$2d ", timeStamp, Calendar.getInstance(), tag);
                if (tag == 1) {
                    currentSnapshot = Snapshot.readSnapshot(reader, currentSnapshot);
                    System.out.println("Snapshot received " + currentSnapshot.snapshotId);
                } else {
                    Client.ServerEvent serverEvent = Client.read(reader, tag, timeStamp, ui, currentSnapshot);
                    if (serverEvent instanceof Client.EJobEvent) {
                        Client.EJobEvent e = (Client.EJobEvent) serverEvent;
                        int jobId = e.jobKey.jobId;
                        assert e.newState == EJob.State.SERVER_DONE;
                        if (jobId > 0) {
                            if (!e.doItOk) {
                                System.out.println("Job " + e.jobName + " failed");
                                passed = false;
                            }
                            continue;
                        }
                        assert jobId == curJobId;
                        if (!e.doItOk) {
                            System.out.println("Job " + e.jobName + " exception");
                            passed = false;
                        } else {
                            System.out.println("Job " + jobId + " ok");
                        }
                        switch (jobId) {
                            case -1:
                                curJobId = -2;
                                break;
                            case -2:
                                curJobId = -3;
                                break;
                            default:
                        }
                    } else {
                        serverEvent.show(ui);
                        if (serverEvent instanceof Client.ShutdownEvent) {
                            assert curJobId == -3;
                            ui.saveMessages(null);
                            return passed;
                        }
                    }
                }
            }
        } catch (IOException e) {
            reader = null;
            System.out.println("END OF FILE reading from server");
            try {
                Thread.sleep(1000);
                System.out.println("Server exit code=" + process.exitValue());
                process.getOutputStream().close();
            } catch (Exception e1) {
                e1.printStackTrace(System.out);
            }
            return false;
        }
    }

    private static void printErrorStream(Process process) {
        try {
//            process.getOutputStream().close();
            InputStream errStream = new BufferedInputStream(process.getErrorStream());
            System.out.println("<StdErr>");
            for (;;) {
                if (errStream.available() == 0) {
                    break;
                }
                int c = errStream.read();
                if (c < 0) {
                    break;
                }
                System.out.print((char) c);
            }
            System.out.println("</StdErr>");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class InitJob extends Job {

        private InitJob() {
            super("InitJob", null, Job.Type.CHANGE, null, null, Job.Priority.USER);
        }

        public boolean doIt() throws JobException {
            database.setToolSettings((Setting.RootGroup) ToolSettings.getToolSettings(""));
            assert database.getGeneric() == null;
            Generic generic = Generic.newInstance(database.getIdManager());
            database.addTech(generic);
            for (TechFactory techFactory : TechFactory.getKnownTechs().values()) {
                Map<TechFactory.Param, Object> paramValues = Collections.emptyMap();
                Technology tech = techFactory.newInstance(generic, paramValues);
                if (tech != null) {
                    database.addTech(tech);
                }
            }
            return true;
        }
    }

    private static class QuitJob extends Job {

        private QuitJob() {
            super("QuitJob", null, Job.Type.CHANGE, null, null, Job.Priority.USER);
        }

        public boolean doIt() throws JobException {
            Client.fireServerEvent(new Client.ShutdownEvent());
            return true;
        }
    }

    private static void writeServerJobs(DataOutputStream clientOutputStream, int connectionId, String script) throws IOException {
        EDatabase database = new EDatabase(IdManager.stdIdManager.getInitialEnvironment());
        Job.setUserInterface(new UserInterfaceInitial(database));

        Job job1 = new InitJob();
        job1.ejob.jobKey = new Job.Key(connectionId, -1, true);

        Job job2 = EvalJavaBsh.runScriptJob(script);
        job2.ejob.jobKey = new Job.Key(connectionId, -2, true);

        Job job3 = new QuitJob();
        job3.ejob.jobKey = new Job.Key(connectionId, -3, true);

        writeEditingPreferences(clientOutputStream, database);
        writeJob(clientOutputStream, job1);
        writeJob(clientOutputStream, job2);
        writeJob(clientOutputStream, job3);
    }

    private static void writeEditingPreferences(DataOutputStream clientOutputStream, EDatabase database) throws IOException {
        byte[] serializedEp;
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            ObjectOutputStream out = new EObjectOutputStream(byteStream, database);
            EditingPreferences ep = new EditingPreferences(true, database.getTechPool());
            out.writeObject(ep);
            out.flush();
            serializedEp = byteStream.toByteArray();
        } catch (Throwable e) {
            e.printStackTrace();
            return;
        }
        clientOutputStream.writeByte((byte) 2);
        clientOutputStream.writeInt(serializedEp.length);
        clientOutputStream.write(serializedEp);
    }

    private static void writeJob(DataOutputStream clientOutputStream, Job job) throws IOException {
        EJob ejob = job.ejob;
        ejob.serialize(EDatabase.clientDatabase());
        clientOutputStream.writeByte((byte) 1);
        clientOutputStream.writeInt(ejob.jobKey.jobId);
        clientOutputStream.writeUTF(ejob.jobType.toString());
        clientOutputStream.writeUTF(ejob.jobName);
        clientOutputStream.writeInt(ejob.serializedJob.length);
        clientOutputStream.write(ejob.serializedJob);
        clientOutputStream.flush();
    }

    /**
     * This class is used to read data from an external process.
     * If something does not consume the data, it will fill up the default
     * buffer and deadlock.  This class also redirects data read
     * from the process (the process' output) to another stream,
     * if specified.
     */
    public static class ExecProcessReader extends Thread {

        private InputStream in;
        private char[] buf;

        /**
         * Create a stream reader that will read from the stream, and
         * store the read text into buffer.
         * @param in the input stream
         */
        public ExecProcessReader(InputStream in) {
            this.in = in;
            buf = new char[256];
            setName("ExecProcessReader");
        }

        public void run() {
            try {
                // read from stream
                InputStreamReader reader = new InputStreamReader(in);
                int read = 0;
                while ((read = reader.read(buf)) >= 0) {
                    String s = new String(buf, 0, read);
                    Calendar c = Calendar.getInstance();
                    System.err.print(s);
                    System.out.format("%1$tT.%1$tL <err> %2$s </err>\n", c, s);
                }

                reader.close();
            } catch (java.io.IOException e) {
                e.printStackTrace();
                e.printStackTrace(System.out);
            }
        }
    }
}
