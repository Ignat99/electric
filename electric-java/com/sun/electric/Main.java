/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: Main.java
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
package com.sun.electric;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JFrame;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Environment;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.Setting;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.technology.Technology;
import com.sun.electric.tool.AbstractUserInterface;
import com.sun.electric.tool.Client;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.UserInterfaceInitial;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.SimulationData;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.user.ActivityLogger;
import com.sun.electric.tool.user.Clipboard;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.menus.FileMenu;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.SimpleWorker;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.UsageFormatter;

/**
 * This class initializes Electric and starts the system. How to run Electric:
 * <P>
 * <P> <CODE>java -jar electric.jar [electric-options]</CODE> without plugins
 * <P> <CODE>java -classpath electric.jar<i>delim</i>{list of plugins} com.sun.electric.Launcher [electric-options]</CODE>
 * otherwise, where <i>delim</i> is OS-dependant separator
 * <P> And Electric options are:
 * <P> <CODE>         -mdi: multiple document interface mode </CODE>
 * <P> <CODE>         -sdi: single document interface mode </CODE>
 * <P> <CODE>         -NOMINMEM: ignore minimum memory provided for JVM </CODE>
 * <P> <CODE>         -s script name: bean shell script to execute </CODE>
 * <P> <CODE>         -version: version information </CODE>
 * <P> <CODE>         -v: brief version information </CODE>
 * <P> <CODE>         -debug: debug mode. Extra information is available </CODE>
 * <P> <CODE>         -server: dump strace of snapshots</CODE>
 * <P> <CODE>         -help: this message </CODE>
 * <P> <P>
 * See manual for more instructions.
 */
public final class Main
{
    /**
     * Mode of Job manager
     */
    private static enum Mode {
        /** Thread-safe full screen run. */                                    FULL_SCREEN_SAFE,
        /** JonG: "I think batch mode implies 'no GUI', and nothing more." */  BATCH,
        /** Server side. */                                                    SERVER,
        /** Client side. */                                                    CLIENT;
    }

    private static final Mode DEFAULT_MODE = Mode.FULL_SCREEN_SAFE;

	private Main() {}

    private static Mode runMode;

    /** JonG: "I think batch mode implies 'no GUI', and nothing more." */
    public static boolean isBatch() {
        return runMode == Mode.BATCH;
    }

    /**
     * Returns true when using Electric unstable features is recommended by system properties.
     * @return true when using Electric unstable features is recommended by system properties.
     */
    public static boolean useUnstableFeatures() {
        return Boolean.getBoolean("electric.unstable");
    }

	/**
	 * The main entry point of Electric.
	 * @param args the arguments to the program.
	 */
	public static void main(String[] args)
	{
		// convert args to array list
        List<String> argsList = new ArrayList<String>();
        for (int i=0; i<args.length; i++) argsList.add(args[i]);

		// -v (short version)
		if (hasCommandLineOption(argsList, "-v"))
		{
			System.out.println(Version.getVersion());
			System.exit(0);
		}

		// -version
		if (hasCommandLineOption(argsList, "-version"))
		{
			System.out.println(Version.getApplicationInformation());
			System.out.println("\t"+Version.getVersionInformation());
			System.out.println("\t"+Version.getCopyrightInformation());
			System.out.println("\t"+Version.getWarrantyInformation());
			System.exit(0);
		}

        // -help
        if (hasCommandLineOption(argsList, "-help"))
		{
	        System.out.println("Usage (without plugins):");
	        System.out.println("\tjava -jar electric.jar [electric-options] [electric-libraries]");
	        System.out.println("\t\twhere [electric-libraries] is list of library files to read");
	        System.out.println("Usage (with plugins):");
	        System.out.println("\tjava -classpath electric.jar[electric-plugins] com.sun.electric.Launcher [electric-options] [electric-libraries]");
	        System.out.println("\t\twhere [electric-plugins] is list of JAR files separated by OS-dependant separator (colon or semicolon)");
	        System.out.println("\nElectric-options:");
	        System.out.println("\t-batch: batch mode implies 'no GUI', and nothing more");
            System.out.println("\t-client <machine name>: replay trace of snapshots");
	        System.out.println("\t-debug: debug mode. Extra information is available");
	        System.out.println("\t-help: this message");
            System.out.println("\t-logging <filePath>: log server events in a binary file");
            System.out.println("\t-logusage <local path>: local path to log usage");
            System.out.println("\t-mdi: multiple document interface mode");
	        System.out.println("\t-NOMINMEM: ignore minimum memory provided for JVM");
	        System.out.println("\t-s <script name>: bean shell script to execute");
	        System.out.println("\t-sdi: single document interface mode");
            System.out.println("\t-server: dump trace of snapshots");
            System.out.println("\t-socket <socket>: socket port for client/server interaction");
            System.out.println("\t-threads <numThreads>: recommended size of thread pool for Job execution.");
	        System.out.println("\t-version: version information");
	        System.out.println("\t-v: brief version information");
			System.exit(0);
		}

		// -debug for debugging
        runMode = DEFAULT_MODE;
        List<String> pipeOptions = new ArrayList<String>();
		if (hasCommandLineOption(argsList, "-debug")) {
            pipeOptions.add(" -debug");
            Job.setDebug(true);
        }
        if (hasCommandLineOption(argsList, "-extraDebug")) {
            pipeOptions.add("-extraDebug");
            Job.LOCALDEBUGFLAG = true;
        }
        String numThreadsString = getCommandLineOption(argsList, "-threads");
        int numThreads = 0 ;
        if (numThreadsString != null) {
            numThreads = TextUtils.atoi(numThreadsString);
            if (numThreads > 0) {
                pipeOptions.add("-threads");
                pipeOptions.add(String.valueOf(numThreads));
            }  else
                System.out.println("Invalid option -threads " + numThreadsString);
        }
        String loggingUsage = getCommandLineOption(argsList, "-logusage");
        if (loggingUsage != null) {
        	UsageFormatter.logUsage("Electric-" + UsageFormatter.getUser() + ".txt", 
        			loggingUsage, false, "");
        }
        String loggingFilePath = getCommandLineOption(argsList, "-logging");
        if (loggingFilePath != null) {
            pipeOptions.add("-logging");
            pipeOptions.add(loggingFilePath);
        }
        String socketString = getCommandLineOption(argsList, "-socket");
        int socketPort = 0;
        if (socketString != null) {
            socketPort = TextUtils.atoi(socketString);
            if (socketPort > 0) {
                pipeOptions.add("-socket");
                pipeOptions.add(String.valueOf(socketPort));
            }  else
                System.out.println("Invalid option -socket " + socketString);
        }
        hasCommandLineOption(argsList, "-NOMINMEM"); // do nothing, just consume option: handled in Launcher

        // The server runs in subprocess
        if (hasCommandLineOption(argsList, "-pipeserver")) {
            ActivityLogger.initialize("electricserver", true, true, true/*false*/, true, false);
            Job.pipeServer(numThreads, loggingFilePath, socketPort);
            return;
        }

        ActivityLogger.initialize("electric", true, true, true/*false*/, User.isEnableLog(), User.isMultipleLog());

        if (hasCommandLineOption(argsList, "-batch")) {
            //System.setProperty("java.awt.headless", "true");
            runMode = Mode.BATCH;
        }
        if (hasCommandLineOption(argsList, "-server")) {
            if (runMode != DEFAULT_MODE)
                System.out.println("Conflicting thread modes: " + runMode + " and " + Mode.SERVER);
            runMode = Mode.SERVER;
        }
        String serverMachineName = getCommandLineOption(argsList, "-client");
        if (serverMachineName != null) {
            if (runMode != DEFAULT_MODE)
                System.out.println("Conflicting thread modes: " + runMode + " and " + Mode.CLIENT);
            runMode = Mode.CLIENT;
        }
        boolean pipe = false;
        boolean pipedebug = false;
        if (hasCommandLineOption(argsList, "-pipe")) {
            if (runMode != DEFAULT_MODE)
                System.out.println("Conflicting thread modes: " + runMode + " and " + Mode.CLIENT);
             runMode = Mode.CLIENT;
           pipe = true;
        }
        if (hasCommandLineOption(argsList, "-pipedebug")) {
            if (runMode != DEFAULT_MODE)
                System.out.println("Conflicting thread modes: " + runMode + " and " + Mode.CLIENT);
            runMode = Mode.CLIENT;
            pipe = true;
            pipedebug = true;
        }

        UserInterfaceMain.Mode mode = null;
        if (hasCommandLineOption(argsList, "-mdi")) mode = UserInterfaceMain.Mode.MDI;
        if (hasCommandLineOption(argsList, "-sdi")) mode = UserInterfaceMain.Mode.SDI;

        final AbstractUserInterface ui = runMode == Mode.FULL_SCREEN_SAFE || runMode == Mode.CLIENT ?
            new UserInterfaceMain(argsList, mode, true) : new UserInterfaceDummy();
        MessagesStream.getMessagesStream();

		// initialize database
        TextDescriptor.cacheSize();
        Tool.initAllTools();
        Pref.lockCreation();
        EDatabase serverDatabase = null;
        EDatabase clientDatabase = null;
        InitDatabase initJob = null;
        switch (runMode) {
            case FULL_SCREEN_SAFE:
                serverDatabase = new EDatabase(IdManager.stdIdManager.getInitialSnapshot(), "serverDB");
                EDatabase.setServerDatabase(serverDatabase);
                clientDatabase = new EDatabase(IdManager.stdIdManager.getInitialSnapshot(), "clientDB");
                EDatabase.setClientDatabase(clientDatabase);
                Job.setUserInterface(new UserInterfaceInitial(clientDatabase));
                initJob = new InitDatabase(argsList);
                EDatabase.setCheckExamine();
                Job.initJobManager(numThreads, loggingFilePath, socketPort, ui, initJob);
                break;
            case CLIENT:
                clientDatabase = new EDatabase(IdManager.stdIdManager.getInitialSnapshot(), "clientDB");
                EDatabase.setClientDatabase(clientDatabase);
                Job.setUserInterface(new UserInterfaceInitial(clientDatabase));
                initJob = new InitDatabase(argsList);
                // Client or pipe mode
                IdManager.stdIdManager.setReadOnly();
                if (pipe) {
                    try {
                        Process process = invokePipeserver(pipeOptions, pipedebug);
                        Job.pipeClient(process, ui, initJob, false/*pipedebug*/);
                    } catch (IOException e) {
                        System.exit(1);
                    }
                } else {
                    Job.socketClient(serverMachineName, socketPort, ui, new InitClient(argsList));
                }
                break;
            case SERVER:
                serverDatabase = new EDatabase(IdManager.stdIdManager.getInitialSnapshot(), "serverDB");
                EDatabase.setServerDatabase(serverDatabase);
                Job.setUserInterface(new UserInterfaceInitial(serverDatabase));
                initJob = new InitDatabase(argsList);
                Job.initJobManager(numThreads, loggingFilePath, socketPort, ui, initJob);
                break;
            case BATCH:
                serverDatabase = new EDatabase(IdManager.stdIdManager.getInitialSnapshot(), "serverDB");
                EDatabase.setServerDatabase(serverDatabase);
                Job.setUserInterface(new UserInterfaceInitial(serverDatabase));
                EditingPreferences.lowLevelSetThreadLocalEditingPreferences(new EditingPreferences(true, null));
                initJob = new InitDatabase(argsList);
                Job.initJobManager(numThreads, loggingFilePath, socketPort, ui, initJob);
                break;
        }

        // initialize parallel stuff
        ThreadPool.PoolWorkerStrategyFactory.userDefinedStrategy = new SimpleWorker(null) {
//        PoolWorkerStrategyFactory.userDefinedStrategy = new SynchronizedWorker(null, null) {

            @Override
            public void execute() {

                try {
                    Job.setUserInterface(ui);
                    Environment.setThreadEnvironment(ui.getDatabase().getEnvironment());
                } catch (Exception ex) {
                }

                super.execute();

            }
        };
	}

    private static Process invokePipeserver(List<String> electricOptions, boolean withDebugger) throws IOException {
        List<String> javaOptions = new ArrayList<String>();
        javaOptions.add("-Xss2m");
		int maxMemWanted = StartupPrefs.getMemorySize();
        javaOptions.add("-Xmx" + maxMemWanted + "m");
        long maxPermWanted = StartupPrefs.getPermSpace();
        if (maxPermWanted > 0)
            javaOptions.add("-XX:MaxPermSize=" + maxPermWanted + "m");
        if (withDebugger) {
            javaOptions.add("-Xdebug");
            javaOptions.add("-Xrunjdwp:transport=dt_socket,server=n,address=localhost:35856");
        }
        return Launcher.invokePipeserver(javaOptions, electricOptions);
    }

    public static class UserInterfaceDummy extends AbstractUserInterface
	{
        public static final PrintStream stdout = System.out;

        public UserInterfaceDummy() {
        }

        public void startProgressDialog(String type, String filePath) {}
        public void stopProgressDialog() {}
        public void setProgressValue(int pct) {}
        public void setProgressNote(String message) {}
        public String getProgressNote() { return null; }

    	public EDatabase getDatabase() {
            return EDatabase.clientDatabase();
        }
		public EditWindow_ getCurrentEditWindow_() { return null; }
		public EditWindow_ needCurrentEditWindow_()
		{
			System.out.println("Batch mode Electric has no needed windows");
			return null;
		}
        /** Get current cell from current library */
		public Cell getCurrentCell()
        {
            throw new IllegalStateException("Batch mode Electric has no current Cell");
        }

		public Cell needCurrentCell()
		{
            throw new IllegalStateException("Batch mode Electric has no current Cell");
		}
		public void repaintAllWindows() {}

        public void adjustReferencePoint(Cell cell, double cX, double cY) {};
		public int getDefaultTextSize() { return 14; }
//		public Highlighter getHighlighter();
		public EditWindow_ displayCell(Cell cell) { return null; }

        public void termLogging(final ErrorLogger logger, boolean explain, boolean terminate) {
            System.out.println(logger.getInfo());
        }

        /**
         * Method to return the error message associated with the current error.
         * Highlights associated graphics if "showhigh" is nonzero.
         */
        public String reportLog(ErrorLogger.MessageLog log, boolean showhigh, boolean separateWindow, int position)
        {
            // return the error message
            return log.getMessageString();
        }

        /**
         * Method to show an error message.
         * @param message the error message to show.
         * @param title the title of a dialog with the error message.
         */
        public void showErrorMessage(String message, String title)
        {
        	System.out.println(message);
        }

        /**
         * Method to show an error message.
         * @param message the error message to show.
         * @param title the title of a dialog with the error message.
         */
        public void showErrorMessage(String[] message, String title)
        {
        	System.out.println(message);
        }

        /**
         * Method to show an informational message.
         * @param message the message to show.
         * @param title the title of a dialog with the message.
         */
        public void showInformationMessage(String message, String title)
        {
        	System.out.println(message);
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
        
        private PrintWriter printWriter = null;

        /**
         * Method print a message.
         * @param message the message to show.
         * @param newLine add new line after the message
         */
        public void printMessage(String message, boolean newLine) {
            if (newLine) {
                stdout.println(message);
                if (printWriter != null)
                    printWriter.println(message);
            } else {
                stdout.print(message);
                if (printWriter != null)
                    printWriter.print(message);
            }
        }

        /**
         * Method to start saving messages.
         * @param filePath file to save
         */
        public void saveMessages(final String filePath) {
            try
            {
                if (printWriter != null) {
                    printWriter.close();
                    printWriter = null;
                }
                if (filePath == null) return;
                printWriter = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
            } catch (IOException e)
            {
                System.err.println("Error creating " + filePath);
                System.out.println("Error creating " + filePath);
                return;
            }

            System.out.println("Messages will be saved to " + filePath);
        }

        /**
         * Method to show a message and ask for confirmation.
         * @param message the message to show.
         * @return true if "yes" was selected, false if "no" was selected.
         */
        public boolean confirmMessage(Object message) { return true; }

        /**
         * Method to ask for a choice among possibilities.
         * @param message the message to show.
         * @param title the title of the dialog with the query.
         * @param choices an array of choices to present, each in a button.
         * @param defaultChoice the default choice.
         * @return the index into the choices array that was selected.
         */
        public int askForChoice(String message, String title, String [] choices, String defaultChoice)
        {
        	System.out.println(message + " CHOOSING " + defaultChoice);
        	for(int i=0; i<choices.length; i++) if (choices[i].equals(defaultChoice)) return i;
        	return 0;
        }

        /**
         * Method to ask for a line of text.
         * @param message the prompt message.
         * @param title the title of a dialog with the message.
         * @param def the default response.
         * @return the string (null if cancelled).
         */
        public String askForInput(Object message, String title, String def) { return def; }

        @Override
        protected void terminateJob(Job.Key jobKey, String jobName, Tool tool,
            Job.Type jobType, byte[] serializedJob,
            boolean doItOk, byte[] serializedResult, Snapshot newSnapshot) {
            printMessage("Job " + jobKey, true);
            if (!jobType.isExamine()) {
                endChanging();
            }
        }

        @Override
        protected void showJobQueue(Job.Inform[] jobQueue) {
            printMessage("JobQueue: ", false);
            for (Job.Inform jobInfo: jobQueue)
                printMessage(" " + jobInfo, false);
            printMessage("", true);
        }

        @Override
        protected void addEvent(Client.ServerEvent serverEvent) {
            serverEvent.run();
        }
	}

	/** check if command line option 'option' present in
     * command line args. If present, return true and remove if from the list.
     * Otherwise, return false.
     */
    private static boolean hasCommandLineOption(List<String> argsList, String option)
    {
        for (int i=0; i<argsList.size(); i++) {
            if (argsList.get(i).equals(option)) {
                argsList.remove(i);
                return true;
            }
        }
        return false;
    }

    /** get command line option for 'option'. Returns null if
     * no such 'option'.  If found, remove it from the list.
     */
    private static String getCommandLineOption(List<String> argsList, String option)
    {
        for (int i=0; i<argsList.size()-1; i++) {
            if (argsList.get(i).equals(option)) {
                argsList.remove(i); // note that this shifts objects in arraylist
                // check if next string valid (i.e. no dash)
                if (argsList.get(i).startsWith("-")) {
                    System.out.println("Bad command line option: "+ option +" "+ argsList.get(i+1));
                    return null;
                }
                return argsList.remove(i);
            }
        }
        return null;
    }

    /** open any libraries specified on the command line.  This method should be
     * called after any valid options have been parsed out
     */
    public static void openCommandLineLibs(List<String> argsList)
    {
        for (int i=0; i<argsList.size(); i++) {
            String arg = argsList.get(i);
            if (arg.startsWith("-")) {
                System.out.println("Command line option "+arg+" not understood, ignoring.");
                continue;
            }
			URL url = TextUtils.makeURLToFile(arg);
            if (url == null) continue;
            if (!isBatch()) User.setWorkingDirectory(TextUtils.getFilePath(url));
            // setting database path for future references
            FileType.JELIB.setGroupPath(User.getWorkingDirectory());
            if (arg.indexOf('.')!=-1 && SimulationData.isKnownSimulationFormatExtension(arg.substring(arg.lastIndexOf('.')+1))) {
                SimulationData.plot(null, url, null);
            } else {
            	// make sure library isn't already there
            	boolean alreadyThere = false;
//System.out.println("CHECKING REDUNDANCY FOR LIBRARY "+url.toString());
            	for(Iterator<Library> it = Library.getLibraries(); it.hasNext(); )
            	{
            		Library lib = it.next();
            		URL lURL = lib.getLibFile();
//System.out.println("   FOUND LIBRARY "+lib.getName()+" ("+lURL+")");
            		if (lURL != null && lURL.equals(url))
            		{
//System.out.println("SKIPPING "+url);		// TODO: debug
            			alreadyThere = true;  break;
            		}
            	}
            	if (!alreadyThere)
            		FileMenu.openLibraryCommand(url);
            }
        }
    }

	/**
	 * Class to initialize all technologies.
	 */
	private static class InitDatabase extends Job
	{
        private Map<String,Object> paramValuesByXmlPath = Technology.getParamValuesByXmlPath();
		private List<String> argsList;
        private Library mainLib;

		private InitDatabase(List<String> argsList)
		{
			super("Init database", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.argsList = argsList;
		}

        @Override
		public boolean doIt() throws JobException
		{
            //System.out.println("InitDatabase");
            // initialize all of the technologies
            Technology.initPreinstalledTechnologies(getDatabase(), paramValuesByXmlPath);

            // open no name library first
            Library clipLib = Library.newInstance(Clipboard.CLIPBOARD_LIBRAY_NAME, null);
            clipLib.setHidden();
            Cell clipCell = Cell.newInstance(clipLib, Clipboard.CLIPBOARD_CELL_NAME);
            assert clipCell.getId().cellIndex == Clipboard.CLIPBOARD_CELL_INDEX;
            clipCell.setTechnology(getTechPool().getGeneric());

            mainLib = Library.newInstance("noname", null);
            if (mainLib == null) return false;
            fieldVariableChanged("mainLib");
            mainLib.clearChanged();

            if (isBatch()) {
                String beanShellScript = getCommandLineOption(argsList, "-s");
                openCommandLineLibs(argsList);
                if (beanShellScript != null)
                    EvalJavaBsh.runScript(beanShellScript);
            }
            return true;
		}

        @Override
        public void terminateOK() {
            new InitProjectSettings(argsList).startJobOnMyResult();
            User.setCurrentLibrary(mainLib);
        }

        @Override
        public void terminateFail(Throwable jobException) {
            System.out.println("Initialization failed");
            System.exit(1);
        }
	}

	/**
	 * Class to init project preferences.
	 */
	private static class InitProjectSettings extends Job
	{
        private Setting.SettingChangeBatch changeBatch = new Setting.SettingChangeBatch();
		List<String> argsList;

		private InitProjectSettings(List<String> argsList)
		{
			super("Init project preferences", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            Preferences prefRoot = Pref.getPrefRoot();
            for (Map.Entry<Setting,Object> e: getDatabase().getSettings().entrySet()) {
                Setting setting = e.getKey();
                Object value = setting.getValueFromPreferences(prefRoot);
                if (value.equals(e.getValue())) continue;
                changeBatch.add(setting, value);
            }
            this.argsList = argsList;
		}

        @Override
		public boolean doIt() throws JobException
		{
            getDatabase().implementSettingChanges(changeBatch);
            return true;
		}

        @Override
        public void terminateOK() {
            Job.getExtendedUserInterface().finishInitialization();
			String beanShellScript = getCommandLineOption(argsList, "-s");
            openCommandLineLibs(argsList);
            if (beanShellScript != null)
                EvalJavaBsh.runScript(beanShellScript);
        }
	}

	/**
	 * Class to init project preferences.
	 */
	private static class InitClient extends Job
	{
        private Setting.SettingChangeBatch changeBatch = new Setting.SettingChangeBatch();
		List<String> argsList;

		private InitClient(List<String> argsList)
		{
			super("Init project preferences", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.argsList = argsList;
		}

        @Override
		public boolean doIt() throws JobException
		{
            return true;
		}

        @Override
        public void terminateOK() {
            Job.getExtendedUserInterface().finishInitialization();
			String beanShellScript = getCommandLineOption(argsList, "-s");
            openCommandLineLibs(argsList);
            if (beanShellScript != null)
                EvalJavaBsh.runScript(beanShellScript);
        }
	}
}
