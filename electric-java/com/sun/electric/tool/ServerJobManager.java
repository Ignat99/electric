/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ServerJobManager.java
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

import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.Main;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.IdWriter;
import com.sun.electric.database.id.LibId;
import com.sun.electric.database.id.TechId;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.user.ActivityLogger;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JFrame;

/**
 *
 */
public class ServerJobManager {

    private static final String CLASS_NAME = Job.class.getName();
    private static final int defaultNumThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
    private final ReentrantLock lock = new ReentrantLock();

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }
    /** mutex for database synchronization. */
    private final Condition databaseChangesMutex = lock.newCondition();
    /** started jobs */
    private final ArrayList<EJob> startedJobs = new ArrayList<EJob>();
    /** waiting jobs */
    private final ArrayList<EJob> waitingJobs = new ArrayList<EJob>();
    private final ServerSocket serverSocket;
//    private final ArrayList<EJob> finishedJobs = new ArrayList<EJob>();
    final ArrayList<Client> serverConnections = new ArrayList<Client>();
    private int passiveConnections;
//    private final UserInterface redirectInterface = new UserInterfaceRedirect();
    private int numThreads;
    private final int maxNumThreads;
    private boolean runningChangeJob;
//    private boolean guiChanged;
    private boolean signalledEThread;
    private static int maxNumberOfThreads;

    /** Creates a new instance of JobPool */
    ServerJobManager(int recommendedNumThreads, String loggingFilePath, boolean pipe, int socketPort) {
        maxNumThreads = initThreads(recommendedNumThreads);
        maxNumberOfThreads = maxNumThreads;
        if (Job.currentUI != null) {
            initCurrentUI(Job.currentUI);
        }
        if (loggingFilePath != null) {
            initSnapshotLogging(new File(loggingFilePath));
        }
        if (pipe) {
            initPipe();
        }
        ServerSocket serverSocket = null;
        if (socketPort > 0) {
            try {
                serverSocket = new ServerSocket(socketPort);
                System.out.println("ServerSocket waits for port " + socketPort);
            } catch (IOException e) {
                System.out.println("ServerSocket mode failure: " + e.getMessage());
            }
        }
        this.serverSocket = serverSocket;
    }

    ServerJobManager() {
        this(0, null, false, 0);
        serverConnections.add(new Client(0) {
        });
    }

    private int initThreads(int recommendedNumThreads) {
        int maxNumThreads = defaultNumThreads;
        if (recommendedNumThreads > 0) {
            maxNumThreads = recommendedNumThreads;
        }
        Job.logger.trace("ServerJobManager.initThreads maxNumThreads=", maxNumThreads);
        return maxNumThreads;
    }

    void initCurrentUI(AbstractUserInterface currentUI) {
        lock();
        try {
            assert currentUI.connectionId == 0;
            assert serverConnections.isEmpty();
            serverConnections.add(currentUI);
        } finally {
            unlock();
        }
        currentUI.startDispatcher();
    }

    void initSnapshotLogging(File loggingFile) {
        StreamClient conn;
        lock();
        try {
            int connectionId = serverConnections.size();
            FileOutputStream out = new FileOutputStream(loggingFile);
            System.err.println("Writing snapshot log to " + loggingFile);
            ActivityLogger.logMessage("Writing snapshot log to " + loggingFile);
            conn = new StreamClient(connectionId, null, new BufferedOutputStream(out));
            serverConnections.add(conn);
            passiveConnections++;
        } catch (IOException e) {
            System.err.println("Failed to create snapshot log file:" + e.getMessage());
            return;
        } finally {
            unlock();
        }
        conn.start();
    }

    void initPipe() {
        StreamClient conn;
        OutputStream stdout = System.out;
        MessagesStream.getMessagesStream();
        lock();
        try {
            int connectionId = serverConnections.size();
            conn = new StreamClient(connectionId, System.in, stdout);
            serverConnections.add(conn);
        } finally {
            unlock();
        }
        conn.start();
    }

    void connectionClosed() {
        lock();
        try {
            passiveConnections++;
            if (passiveConnections == serverConnections.size()) {
                try {
                    ActivityLogger.finished();
                } catch (Exception e) {
                }
                System.exit(0);
            }
        } finally {
            unlock();
        }
    }

    /** Add job to list of jobs */
    void addJob(EJob ejob, boolean onMySnapshot) {
        lock();
        try {
            if (onMySnapshot) {
                waitingJobs.add(0, ejob);
            } else {
                waitingJobs.add(ejob);
            }
            setEJobState(ejob, EJob.State.WAITING, onMySnapshot ? EJob.WAITING_NOW : "waiting");
            invokeEThread();
        } finally {
            unlock();
        }
    }

    /** Remove job from list of jobs */
    void removeJob(Job j) {
        EJob ejob = j.ejob;
        lock();
        try {
            switch (j.ejob.state) {
                case WAITING:
                    setEJobState(ejob, EJob.State.SERVER_DONE, null);
                case SERVER_DONE:
                    setEJobState(ejob, EJob.State.CLIENT_DONE, null);
                case CLIENT_DONE:
//                    finishedJobs.remove(j.ejob);
//                    if (!Job.BATCHMODE && !guiChanged)
//                        SwingUtilities.invokeLater(this);
//                    guiChanged = true;
                    break;
            }
        } finally {
            unlock();
        }
    }

    void setProgress(EJob ejob, String progress) {
        lock();
        try {
            if (ejob.state == EJob.State.RUNNING) {
                setEJobState(ejob, EJob.State.RUNNING, progress);
            }
        } finally {
            unlock();
        }
    }

    /** get all jobs iterator */
    Iterator<Job> getAllJobs() {
        lock();
        try {
            ArrayList<Job> jobsList = new ArrayList<Job>();
            for (EJob ejob : startedJobs) {
                Job job = ejob.getJob();
                if (job != null) {
                    jobsList.add(job);
                }
            }
            for (EJob ejob : waitingJobs) {
                Job job = ejob.getJob();
                if (job != null) {
                    jobsList.add(job);
                }
            }
            return jobsList.iterator();
        } finally {
            unlock();
        }
    }

    List<Job.Inform> getAllJobInforms() {
        lock();
        try {
            ArrayList<Job.Inform> jobsList = new ArrayList<Job.Inform>();
            for (EJob ejob : startedJobs) {
                Job job = ejob.getJob();
                if (job != null) {
                    jobsList.add(job.getInform());
                } else {
                    jobsList.add(ejob.getInform());
                }
            }
            for (EJob ejob : waitingJobs) {
                Job job = ejob.getJob();
                if (job != null) {
                    jobsList.add(job.getInform());
                } else {
                    jobsList.add(ejob.getInform());
                }
            }
            return jobsList;
        } finally {
            unlock();
        }
    }

    //--------------------------PRIVATE JOB METHODS--------------------------
    private void invokeEThread() {
        if (signalledEThread || startedJobs.size() >= maxNumThreads) {
            return;
        }
        if (!canDoIt()) {
            return;
        }
        if (startedJobs.size() < numThreads) {
            databaseChangesMutex.signal();
        } else {
            new EThread(numThreads++);
        }
        signalledEThread = true;
    }

//    private EJob selectTerminateIt() {
//        lock();
//        try {
//            for (int i = 0; i < finishedJobs.size(); i++) {
//                EJob ejob = finishedJobs.get(i);
//                if (ejob.state == EJob.State.CLIENT_DONE) continue;
////                finishedJobs.remove(i);
//                return ejob;
//            }
//        } finally {
//            unlock();
//        }
//        return null;
//    }
//
//    void wantUpdateGui() {
//        lock();
//        try {
//            this.guiChanged = true;
//        } finally {
//            unlock();
//        }
//    }
//
//    private boolean guiChanged() {
//        lock();
//        try {
//            boolean b = this.guiChanged;
//            this.guiChanged = false;
//            return b;
//        } finally {
//            unlock();
//        }
//    }
    private boolean canDoIt() {
        if (waitingJobs.isEmpty()) {
            return false;
        }
        EJob ejob = waitingJobs.get(0);
        return startedJobs.isEmpty() || !runningChangeJob && ejob.isExamine();
    }

    private void setEJobState(EJob ejob, EJob.State newState, String info) {
        Job.logger.trace("enter ServerJobManager.setEjobState {} {}", newState, ejob.jobName);
        EJob.State oldState = ejob.state;
        switch (newState) {
            case WAITING:
                break;
            case RUNNING:
                if (oldState == EJob.State.RUNNING) {
                    assert oldState == EJob.State.RUNNING;
                    ejob.progress = info;
                    if (info.equals(EJob.ABORTING)) {
                        ejob.serverJob.scheduledToAbort = true;
                    }
                }
                break;
            case SERVER_DONE:
                boolean removed;
                if (oldState == EJob.State.WAITING) {
                    removed = waitingJobs.remove(ejob);
                } else {
                    assert oldState == EJob.State.RUNNING;
                    removed = startedJobs.remove(ejob);
                    if (startedJobs.isEmpty()) {
                        runningChangeJob = false;
                    }
                }
                assert removed;
//                if (Job.threadMode != Job.Mode.BATCH && ejob.client == null)
//                    finishedJobs.add(ejob);
                ejob.state = newState;
                Client.fireServerEvent(new Client.EJobEvent(ejob.jobKey, ejob.jobName,
                        ejob.getJob().getTool(), ejob.jobType, ejob.serializedJob,
                        ejob.doItOk, ejob.serializedResult, ejob.newSnapshot, ejob.state));
                break;
            case CLIENT_DONE:
                assert oldState == EJob.State.SERVER_DONE;
//                if (ejob.clientJob.deleteWhenDone)
//                    finishedJobs.remove(ejob);
        }
        ejob.state = newState;
        List<Job.Inform> jobs = getAllJobInforms();
        Client.fireServerEvent(new Client.JobQueueEvent(jobs.toArray(new Job.Inform[jobs.size()])));

//        if (!Job.BATCHMODE && !guiChanged)
//            SwingUtilities.invokeLater(this);
//        guiChanged = true;
        Job.logger.trace("exiting setJobState");
    }

//    private boolean isChangeJobQueuedOrRunning() {
//        lock();
//        try {
//            for (EJob ejob: startedJobs) {
//                Job job = ejob.getJob();
//                if (job != null && job.finished) continue;
//                if (ejob.jobType == Job.Type.CHANGE) return true;
//            }
//            for (EJob ejob: waitingJobs) {
//                if (ejob.jobType == Job.Type.CHANGE) return true;
//            }
//            return false;
//        } finally {
//            unlock();
//        }
//    }
    public void runLoop(Job initialJob) {

        initialJob.startJob();

        if (serverSocket == null) {
            return;
        }
        try {
            // Wait for connections
            for (;;) {
                Socket socket = serverSocket.accept();
                int connectionId = serverConnections.size();
                StreamClient conn;
                lock();
                try {
                    conn = new StreamClient(connectionId, socket.getInputStream(), socket.getOutputStream());
                    serverConnections.add(conn);
                } finally {
                    unlock();
                }
                System.out.println("Accepted connection " + connectionId);
                conn.start();
            }
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }
    }

//    /**
//     * This method is executed in Swing thread.
//     */
//    public void run() {
//        assert !Job.BATCHMODE;
//        Job.logger.logp(Level.FINE, CLASS_NAME, "run", "ENTER");
//        while (guiChanged()) {
//            ArrayList<Job.Inform> jobs = new ArrayList<Job.Inform>();
//            for (Iterator<Job> it = Job.getAllJobs(); it.hasNext();) {
//                Job j = it.next();
//                if (j.getDisplay()) {
//                    jobs.add(j.getInform());
//                }
//            }
//            JobTree.update(jobs);
//            TopLevel.setBusyCursor(isChangeJobQueuedOrRunning());
////            for (;;) {
////                EJob ejob = selectTerminateIt();
////                if (ejob == null) break;
////
////                Job.logger.logp(Level.FINE, CLASS_NAME, "run", "terminate {0}", ejob.jobName);
////                Job.runTerminate(ejob);
////                setEJobState(ejob, EJob.State.CLIENT_DONE, null);
////                Job.logger.logp(Level.FINE, CLASS_NAME, "run", "terminated {0}", ejob.jobName);
////            }
//            Job.logger.logp(Level.FINE, CLASS_NAME, "run", "wantToRedoJobTree");
//        }
//        Job.logger.logp(Level.FINE, CLASS_NAME, "run", "EXIT");
//    }
    EJob selectEJob() {
        lock();
        try {
            for (;;) {
                signalledEThread = false;
                // Search for examine
                if (canDoIt()) {
                    EJob ejob = waitingJobs.remove(0);
                    startedJobs.add(ejob);
                    if (ejob.isExamine()) {
                        assert !runningChangeJob;
                        invokeEThread();
                    } else {
                        assert startedJobs.size() == 1;
                        assert !runningChangeJob;
                        runningChangeJob = true;
                    }
                    setEJobState(ejob, EJob.State.RUNNING, "running");
                    return ejob;
                }
                if (Main.isBatch() && startedJobs.isEmpty()) {
                    ActivityLogger.finished();
                    System.exit(0);
                }
                Job.logger.trace("ServerJobManager.selectEJob pause");
                databaseChangesMutex.awaitUninterruptibly();
                Job.logger.trace("ServerJobManager.selectEJob resume");
            }
        } finally {
            unlock();
        }
    }

    void finishEJob(EJob finishedEJob) {
        lock();
        try {
            setEJobState(finishedEJob, EJob.State.SERVER_DONE, "done");
        } finally {
            unlock();
        }
    }

    /*private*/ static class UserInterfaceRedirect implements UserInterface {

        private final Job.Key jobKey;
        private final Client client;
        private final EDatabase database;
        TechId curTechId;
        LibId curLibId;
        CellId curCellId;
        private String progressNote;
        private int progressValue = -1;

        UserInterfaceRedirect(Job.Key jobKey) {
            this.jobKey = jobKey;
            client = Job.serverJobManager.serverConnections.get(jobKey.clientId);
            database = jobKey.doItOnServer ? EDatabase.serverDatabase() : EDatabase.clientDatabase();
        }

        UserInterfaceRedirect(Job.Key jobKey, AbstractUserInterface client) {
            this.jobKey = jobKey;
            this.client = client;
            assert !jobKey.doItOnServer;
            database = EDatabase.clientDatabase();
        }

        private UserInterfaceRedirect(EDatabase database) {
            jobKey = null;
            client = null;
            this.database = database;
        }

        void setCurrents(Job job) {
            assert jobKey == job.getKey();
            curTechId = job.curTechId;
            curLibId = job.curLibId;
            curCellId = job.curCellId;
        }

        private static void printStackTrace(String methodName) {
            if (!Job.getDebug()) {
                return;
            }
            System.out.println("UserInterface." + methodName + " was called from DatabaseChangesThread");
            ActivityLogger.logException(new IllegalStateException());
        }

        /**
         * Method to start the display of a progress dialog.
         * @param msg the message to show in the progress dialog.
         * @param the file being read (null if not reading a file).
         */
        @Override
        public void startProgressDialog(String msg, String filePath) {
            progressValue = -1;
            Client.fireServerEvent(new Client.StartProgressDialogEvent(msg, filePath));
        }

        /**
         * Method to stop the progress bar
         */
        @Override
        public void stopProgressDialog() {
            progressValue = -1;
            Client.fireServerEvent(new Client.StopProgressDialogEvent());
        }

        /**
         * Method to update the progress bar
         * @param pct the percentage done (from 0 to 100).
         */
        @Override
        public void setProgressValue(int pct) {
            if (pct == progressValue) {
                return;
            }
            progressValue = pct;
            Client.fireServerEvent(new Client.ProgressValueEvent(pct));
        }

        /**
         * Method to set a text message in the progress dialog.
         * @param message the new progress message.
         */
        @Override
        public void setProgressNote(String message) {
            progressNote = message;
            progressValue = -1;
            Client.fireServerEvent(new Client.ProgressNoteEvent(message));
        }

        /**
         * Method to get text message in the progress dialog.
         * @return text message in the progress dialog.
         */
        @Override
        public String getProgressNote() {
            return progressNote;
        }

        @Override
        public Job.Key getJobKey() {
            return jobKey;
        }

        @Override
        public EDatabase getDatabase() {
            return database;
        }
        
        @Override
        public Technology getCurrentTechnology() {
            Technology tech = null;
            if (curTechId != null) {
                tech = database.getTech(curTechId);
            }
            if (tech == null) {
                tech = database.getTechPool().findTechnology(User.getDefaultTechnology());
            }
            if (tech == null) {
                tech = database.getTechPool().findTechnology("mocmos");
            }
            return tech;
        }

        @Override
        public Library getCurrentLibrary() {
            return curLibId != null ? database.getLib(curLibId) : null;
        }

        @Override
        public EditWindow_ getCurrentEditWindow_() {
            printStackTrace("getCurrentEditWindow");
            return null;
        }

        @Override
        public EditWindow_ needCurrentEditWindow_() {
            printStackTrace("needCurrentEditWindow");
            return null;
        }

        /** Get current cell from current library */
        @Override
        public Cell getCurrentCell() {
            return curCellId != null ? database.getCell(curCellId) : null;
        }

        @Override
        public Cell needCurrentCell() {
            Cell cell = getCurrentCell();
            if (cell != null) {
                return cell;
            }
            throw new IllegalStateException("Can't get current Cell in database thread");
        }

        @Override
        public void repaintAllWindows() {
            printStackTrace("repaintAllWindows");
//            Job.currentUI.repaintAllWindows();
        }

        @Override
        public void adjustReferencePoint(Cell cell, double cX, double cY) {
//            Job.currentUI.adjustReferencePoint(cell, cX, cY);
        }

        ;

        @Override
        public int getDefaultTextSize() {
            return 14;
        }

        @Override
        public EditWindow_ displayCell(Cell cell) {
            throw new IllegalStateException();
        }

        @Override
        public void termLogging(final ErrorLogger logger, boolean explain, boolean terminate) {
            Client.fireServerEvent(new Client.TermLoggingEvent(logger, explain, terminate));
        }

        public void updateNetworkErrors(Cell cell, List<ErrorLogger.MessageLog> errors) {
            throw new IllegalStateException();
        }

        public void updateIncrementalDRCErrors(Cell cell, List<ErrorLogger.MessageLog> errors) {
            throw new IllegalStateException();
        }

        /**
         * Method to return the error message associated with the current error.
         * Highlights associated graphics if "showhigh" is nonzero.
         */
        @Override
        public String reportLog(ErrorLogger.MessageLog log, boolean showhigh, boolean separateWindow, int position) {
            printStackTrace("reportLog");
            // return the error message
            return log.getMessageString();
        }

        /**
         * Method to show an error message.
         * @param message the error message to show.
         * @param title the title of a dialog with the error message.
         */
        @Override
        public void showErrorMessage(String message, String title) {
            Client.fireServerEvent(new Client.ShowMessageEvent(client, message, title, true));
        }

        /**
         * Method to show an informational message.
         * @param message the message to show.
         * @param title the title of a dialog with the message.
         */
        @Override
        public void showInformationMessage(String message, String title) {
            Client.fireServerEvent(new Client.ShowMessageEvent(client, message, title, false));
        }

        /**
         * Method to show an informational message.
         * @param frame top window to use. It could be null
         * @param message the message to show.
         * @param title the title of a dialog with the message.
         */
        public void showInformationMessage(JFrame frame, final String message, final String title) 
        {
        	showInformationMessage(message, title);
        }
        
        /**
         * Method print a message.
         * @param message the message to show.
         * @param newLine add new line after the message
         */
        @Override
        public void printMessage(String message, boolean newLine) {
            if (newLine) {
                message += "\n";
            }
            int i = 0;
            while (i < message.length()) {
                int l = Math.min(IdWriter.MAX_STR_LENGTH, message.length() - i);
                Client.fireServerEvent(new Client.PrintEvent(client,
                    message.substring(i, i + l)));
                i += l;
            }
        }

        /**
         * Method to start saving messages.
         * @param filePath file to save
         */
        @Override
        public void saveMessages(String filePath) {
            Client.fireServerEvent(new Client.SavePrintEvent(client, filePath));
        }

        /**
         * Method to beep.
         */
        @Override
        public void beep() {
            Client.fireServerEvent(new Client.BeepEvent());
        }

        /**
         * Method to show a message and ask for confirmation.
         * @param message the message to show.
         * @return true if "yes" was selected, false if "no" was selected.
         */
        @Override
        public boolean confirmMessage(Object message) {
            printStackTrace("confirmMessage");
            return true;
        }

        /**
         * Method to ask for a choice among possibilities.
         * @param message the message to show.
         * @param title the title of the dialog with the query.
         * @param choices an array of choices to present, each in a button.
         * @param defaultChoice the default choice.
         * @return the index into the choices array that was selected.
         */
        @Override
        public int askForChoice(String message, String title, String[] choices, String defaultChoice) {
            throw new IllegalStateException(message);
        }

        /**
         * Method to ask for a line of text.
         * @param message the prompt message.
         * @param title the title of a dialog with the message.
         * @param def the default response.
         * @return the string (null if cancelled).
         */
        @Override
        public String askForInput(Object message, String title, String def) {
            throw new IllegalStateException();
        }

        /**
         * Save current state of highlights and return its ID.
         */
        public int saveHighlights() {
            return -1;
        }

        /**
         * Restore state of highlights by its ID.
         * @param highlightsId id of saved highlights.
         */
        public void restoreHighlights(int highlightsId) {
        }

        /**
         * Show status of undo/redo buttons
         * @param newUndoEnabled new status of undo button.
         * @param newRedoEnabled new status of redo button.
         */
        public void showUndoRedoStatus(boolean newUndoEnabled, boolean newRedoEnabled) {
        }
    }

    public static int getDefaultNumberOfThreads() {
        return defaultNumThreads;
    }

    public static int getMaxNumberOfThreads() {
        return maxNumberOfThreads;
    }
}
