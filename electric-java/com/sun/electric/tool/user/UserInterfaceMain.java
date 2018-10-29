/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: UserInterfaceMain.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user;

import com.sun.electric.StartupPrefs;
import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Environment;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.change.Undo;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.id.IdManager;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.technology.TechPool;
import com.sun.electric.tool.AbstractUserInterface;
import com.sun.electric.tool.Client;
import com.sun.electric.tool.EJob;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.Listener;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.ToolSettings;
import com.sun.electric.tool.user.dialogs.Progress;
import com.sun.electric.tool.user.ui.ClickZoomWireListener;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.ErrorLoggerTree;
import com.sun.electric.tool.user.ui.JobTree;
import com.sun.electric.tool.user.ui.LayerVisibility;
import com.sun.electric.tool.user.ui.MessagesWindow;
import com.sun.electric.tool.user.ui.ToolBar;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowContent;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.util.ClientOS;
import com.sun.electric.util.math.FixpTransform;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.EventListenerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to build the UserInterface for the main GUI version of the user interface.
 */
public class UserInterfaceMain extends AbstractUserInterface {

    static final Logger logger = LoggerFactory.getLogger("com.sun.electric.tool.user");

    /**
     * Describe the windowing mode.  The current modes are MDI and SDI.
     */
    public static enum Mode {

        MDI, SDI
    }
//	/** Property fired if ability to Undo changes */	public static final String propUndoEnabled = "UndoEnabled";
//	/** Property fired if ability to Redo changes */	public static final String propRedoEnabled = "RedoEnabled";
    static volatile boolean initializationFinished = false;
    private static volatile boolean undoEnabled = false;
    private static volatile boolean redoEnabled = false;
//    private static final EventListenerList undoRedoListenerList = new EventListenerList();
    private static EventListenerList listenerList = new EventListenerList();
    private static Snapshot currentSnapshot = IdManager.stdIdManager.getInitialSnapshot();
    private static GraphicsPreferences currentGraphicsPreferences = null;
//    private static EDatabase database = EDatabase.clientDatabase();
    /** The progress during input. */
    protected static Progress progress = null;
    private SplashWindow sw = null;
//    private PrintStream stdout = System.out;

    public UserInterfaceMain(List<String> argsList, Mode mode, boolean showSplash) {
        // Pushing new EventQueue failes on JDK 7
        // sometimes failes if old EventQueue hasn't init EventDispatchThread
        // So we push a dummy Runnable to init it
        logger.trace("enter UserInterfaceMain constructor");
        try {
            EventQueue.invokeAndWait(new Runnable() {

                public void run() {
                    logger.trace("init original EventDispatchThread {}", Thread.currentThread().getId());
                    assert SwingUtilities.isEventDispatchThread();
                }
            });
        } catch (Exception e) {
            logger.error("Error initing original EventDispatchThread", e);
        }

        // Push a new EventQueue that catches all Throwables
        logger.trace("before pushing new EventQueue");
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {

            @Override
            protected void dispatchEvent(AWTEvent e) {
                if (logger.isTraceEnabled()) {
                    logger.trace("enter dispatchEvent {} in thread {}", e, Thread.currentThread().getId());
                }
                try {
                    super.dispatchEvent(e);
                } catch (Throwable ex) {
                    logger.error("dispatchEvent", ex);
                    ActivityLogger.logException(ex);
//                    if (ex instanceof Error && (!(ex instanceof AssertionError))) {
//                        throw (Error)ex;
//                    }
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("exiting dispatchEvent");
                }
            }
        });
        logger.trace("after pushing new EventQueue");

        // JDK 6 creates a new EventDispatchThread on the new EventQueue
        // JDK 7 reuses the original EventDispatchThread on the new EventQueue
        // Anyway, set client thread to EventDispatchThread of the new EventQueue
        try {
            EventQueue.invokeAndWait(new Runnable() {

                public void run() {
                    logger.trace("set client thread to the new EventDispatchThread {}", Thread.currentThread().getId());
                    assert SwingUtilities.isEventDispatchThread();
                    setClientThread();
                    Environment.setThreadEnvironment(IdManager.stdIdManager.getInitialEnvironment());
                }
            });
        } catch (Exception e) {
            logger.error("Error checking client thread", e);
        }
        logger.trace("after setting client thread");

        if (mode == null) {
            int defMode = StartupPrefs.getDisplayStyle();
            if (defMode == 1) {
                mode = UserInterfaceMain.Mode.MDI;
            } else if (defMode == 2) {
                mode = UserInterfaceMain.Mode.SDI;
            }
        }
        SwingUtilities.invokeLater(new InitializationRun(argsList, mode, showSplash));
    }
    private Snapshot oldUndoSnapshot = null;

    @Override
    protected void showSnapshot(Snapshot newSnapshot) {
        if (oldUndoSnapshot == null) {
            oldUndoSnapshot = currentSnapshot;
        }
        showSnapshot(newSnapshot, true);
    }

    @Override
    protected void terminateJob(Job.Key jobKey, String jobName, Tool tool,
            Job.Type jobType, byte[] serializedJob,
            boolean doItOk, byte[] serializedResult, Snapshot newSnapshot) {

        boolean undoRedo = jobType == Job.Type.UNDO;
        if (jobType != Job.Type.SERVER_EXAMINE && jobType != Job.Type.CLIENT_EXAMINE) {
            Snapshot oldSnapshot = currentSnapshot;
            if (oldUndoSnapshot != null) {
                oldSnapshot = oldUndoSnapshot;
                oldUndoSnapshot = null;
            }
        	// checking if something went wrong with the GUI initialization process
        	if (Job.getUserInterface() == null)
        	{
                System.out.println("Error in GUI Initialization '" + jobName + "'");
                try {
					throw new Exception("Can't start GUI");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					ActivityLogger.logException(e);
				}
                return;
        	}
            int restoredHighlights = Undo.endChanges(oldSnapshot, tool, jobName, newSnapshot);
            showSnapshot(newSnapshot, undoRedo);
            restoreHighlights(restoredHighlights);
        }

        if (jobKey.clientId != getConnectionId()) {
            return;
        }

        EJob ejob;
        Throwable jobException = null;
        if (jobKey.startedByServer()) {
            ejob = new EJob(this, jobKey.jobId, jobType, jobName, serializedJob);
            jobException = ejob.deserializeToClient();
        } else {
            ejob = removeProcessingEJob(jobKey);
        }
        if (jobException != null) {
            System.out.println("Error deserializing '" + jobName + "'");
            ActivityLogger.logException(jobException);
            return;
        }
        ejob.serializedResult = serializedResult;
        jobException = ejob.deserializeResult();

        Job job = ejob.clientJob;
        if (job == null) {
            ActivityLogger.logException(jobException);
            return;
        }
        try {
            job.terminateIt(jobException);
        } catch (Throwable ex) {
            System.out.println("Exception executing terminateIt");
            ex.printStackTrace(System.out);
        }
        job.timer.end();
        job.finished = true;                        // is this redundant with Thread.isAlive()?

        // say something if it took more than a minute by default
        if (job.reportExecution || job.timer.getTime() >= Job.MIN_NUM_SECONDS) {

            if (User.isBeepAfterLongJobs()) {
                Job.getExtendedUserInterface().beep();
            }
            System.out.println(job.getInfo());
        }
    }

    @Override
    protected void showJobQueue(final Job.Inform[] jobQueue) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    showJobQueue(jobQueue);
                }
            });
            return;
        }
        boolean busyCursor = false;
        for (Job.Inform jobInform : jobQueue) {
            if (jobInform.isChangeJobQueuedOrRunning()) {
                busyCursor = true;
            }
        }
        JobTree.update(Arrays.asList(jobQueue));
        TopLevel.setBusyCursor(busyCursor);
    }

    public void addEvent(Client.ServerEvent serverEvent) {
        SwingUtilities.invokeLater(serverEvent);
    }

    private static String getMacClassName() {
        return "com.sun.electric.tool.user.MacOSXInterface";
    }

//    private class InitializationSetJob implements Runnable {
//        Job initJob;
//        public InitializationSetJob(Job job)
//        {
//            this.initJob = job;
//        }
//        public void run()
//        {
//            if (!Client.isOSMac()) return;
//
//            try {
//                Class<?> osXClass = Class.forName(getMacClassName());
//                Method osXSetJobMethod = null;
//
//                // find the necessary methods on the Mac OS/X class
//                try {
//                    osXSetJobMethod = osXClass.getMethod("setInitJob", new Class[] {Job.class});
//                } catch (NoSuchMethodException e) {
//                    osXSetJobMethod = null;
//                }
//                if (osXSetJobMethod != null) {
//                    try {
//                        osXSetJobMethod.invoke(osXClass, new Object[] {initJob});
//                    } catch (Exception e) {
//                        System.out.println("Error initializing Mac OS/X interface");
//                    }
//                }
//            } catch (ClassNotFoundException e) {}
//        }
//    }
    private class InitializationRun implements Runnable {

        List<String> argsList;
        Mode mode;
        boolean showSplash;

        InitializationRun(List<String> argsList, Mode mode, boolean showSplash) {
            this.argsList = argsList;
            this.mode = mode;
            this.showSplash = showSplash;
        }

        public void run() {
            assert Job.isClientThread();
            Pref.setCachedObjsFromPreferences();
            TechPool techPool = new TechPool(IdManager.stdIdManager);
            EditingPreferences.lowLevelSetThreadLocalEditingPreferences(lastSavedEp = new EditingPreferences(true, techPool));
            currentGraphicsPreferences = new GraphicsPreferences(true, techPool);
            // see if there is a Macintosh OS/X interface
            if (ClientOS.isOSMac()) {
                try {
                    Class<?> osXClass = Class.forName(getMacClassName());
                    Method osXRegisterMethod = null;

                    // find the necessary methods on the Macintosh OS/X class
                    try {
                        osXRegisterMethod = osXClass.getMethod("registerMacOSXApplication", new Class[]{List.class});
                    } catch (NoSuchMethodException e) {
                        osXRegisterMethod = null;
                    }
                    if (osXRegisterMethod != null) {
                        try {
                            osXRegisterMethod.invoke(osXClass, new Object[]{argsList});
                        } catch (Exception e) {
                            System.out.println("Error initializing Mac OS/X interface");
                        }
                    }
                } catch (ClassNotFoundException e) {
                }
            }

            //runThreadStatusTimer();

            if (showSplash) {
                sw = new SplashWindow();
            }

            TopLevel.OSInitialize(mode);
            TopLevel.InitializeMessagesWindow();
        }
    }

    /**
     * Method is called when initialization was finished.
     */
    public void finishInitialization() {
        initializationFinished = true;
        // default is last used dir
        if (ClientOS.isOSLinux()) {
            // switch to current dir
            User.setWorkingDirectory(System.getProperty("user.dir"));
        }

        if (sw != null) {
            sw.removeNotify();
            sw = null;
        }
        TopLevel.InitializeWindows();
        WindowFrame.wantToOpenCurrentLibrary(true, null);

        // report on missing components
        if (Job.getDebug()) {
            Set<String> missingComponents = TextUtils.getMissingComponentNames();
            String errorMsg = null;
            for (String comp : missingComponents) {
                if (errorMsg == null) {
                    errorMsg = "Warning: optional components not found: ";
                } else {
                    errorMsg += ", ";
                }
                errorMsg += comp;
            }
            if (errorMsg != null) {
                System.out.println(errorMsg);
            }

            missingComponents = TextUtils.getMissingPrivateComponentNames();
            errorMsg = null;
            for (String comp : missingComponents) {
                if (errorMsg == null) {
                    errorMsg = "Warning: private components not found: ";
                } else {
                    errorMsg += ", ";
                }
                errorMsg += comp;
            }
            if (errorMsg != null) {
                System.out.println(errorMsg);
            }
        }
    }

    public EDatabase getDatabase() {
        return EDatabase.clientDatabase();
    }

    public EditWindow_ getCurrentEditWindow_() {
        return EditWindow.getCurrent();
    }

    public EditWindow_ needCurrentEditWindow_() {
        return EditWindow.needCurrent();
    }

    public Cell getCurrentCell() {
        return WindowFrame.getCurrentCell();
    }

    public Cell needCurrentCell() {
        return WindowFrame.needCurCell();
    }

    /**
     * Method to adjust reference point in WindowFrame containing the cell
     */
    public void adjustReferencePoint(Cell theCell, double cX, double cY) {
        // adjust all windows showing this cell
        for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext();) {
            WindowFrame wf = it.next();
            WindowContent content = wf.getContent();
            if (!(content instanceof EditWindow_)) {
                continue;
            }
            Cell cell = content.getCell();
            if (cell != theCell) {
                continue;
            }
            EditWindow_ wnd = (EditWindow_) content;
            Point2D off = wnd.getOffset();
            off.setLocation(off.getX() - cX, off.getY() - cY);
            wnd.setOffset(off);
        }
    }

    public void repaintAllWindows() {
        WindowFrame.repaintAllWindows();
    }

//    public void loadComponentMenuForTechnology()
//    {
//        WindowFrame wf = WindowFrame.getCurrentWindowFrame(false);
//        if (wf != null) wf.loadComponentMenuForTechnology();
//    }
    public int getDefaultTextSize() {
        return EditWindow.getDefaultFontSize();
    }

    public EditWindow_ displayCell(Cell cell) {
        WindowFrame wf = WindowFrame.getCurrentWindowFrame();
        if (User.isShowCellsInNewWindow()) {
            wf = null;
        }
        if (wf == null) {
            wf = WindowFrame.createEditWindow(cell);
        }
        wf.setCellWindow(cell, null);
        if (wf.getContent() instanceof EditWindow_) {
            return (EditWindow_) wf.getContent();
        }
        return null;
    }

    // ErrorLogger functions
    public void termLogging(final ErrorLogger log, boolean explain, boolean terminate) {
        if (!log.isPersistent() && log.getNumLogs() == 0) {
            return;
        }

        ErrorLoggerTree.addLogger(log, explain, terminate);
    }

    public void updateNetworkErrors(Cell cell, List<ErrorLogger.MessageLog> errors) {
        ErrorLoggerTree.updateNetworkErrors(cell, errors);
    }

    public void updateIncrementalDRCErrors(Cell cell, List<ErrorLogger.MessageLog> newErrors,
            List<ErrorLogger.MessageLog> delErrors) {
        ErrorLoggerTree.updateDrcErrors(cell, newErrors, delErrors);
    }

    /**
     * Method to return the error message associated with the current error.
     * Highlights associated graphics if "showhigh" is nonzero.
     */
    public String reportLog(ErrorLogger.MessageLog log, boolean showhigh, boolean separateWindow, int position) {
        EDatabase database = EDatabase.clientDatabase();

        // show the error
        if (showhigh) {
            // in case multiple windows are involved in the action so all will be brought up
            // and properly terminated for display
            Set<EditWindow> updatedWindows = new HashSet<EditWindow>();

            // clear all highlighting
            for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext();) {
                WindowFrame wf = it.next();
                if (wf.getContent() instanceof EditWindow) {
                    EditWindow wnd = (EditWindow) wf.getContent();
                    Highlighter highlighter = wnd.getHighlighter();
                    highlighter.clear();
                }
            }

            // first show the geometry associated with this error
            int pos = -1;
            for (Iterator<ErrorHighlight> it = log.getHighlights(); it.hasNext();) {
                ErrorHighlight eh = it.next();
                Highlighter highlighter = null;

                // Checking whether a specific geometry is displayed
                pos++;
                if (position != -1 && pos != position) {
                    continue; // not this one
                }
                Cell cell = eh.getCell(database);

                // validate the cell (it may have been deleted)
                if (cell != null) {
                    if (!cell.isLinked()) {
                        return "(cell deleted): " + log.getMessageString();
                    }

                    // make sure it is shown
                    EditWindow wnd;
                    if (separateWindow) {
                        wnd = EditWindow.showEditWindowForCell(cell, eh.getVarContext());
                    } else {
                        wnd = EditWindow.getCurrent();
                        if (wnd != null) {
                            if (wnd.getCell() != cell) {
                                wnd.setCell(cell, eh.getVarContext(), null);
                            }
                        } else {
                            wnd = EditWindow.showEditWindowForCell(cell, eh.getVarContext());
                        }
                    }

                    // nothing clean yet
                    highlighter = wnd.getHighlighter();
                    updatedWindows.add(wnd);
                } else {
                    System.out.println("No cell associated with this error");
                }

                if (highlighter == null) {
                    continue;
                }

                eh.addToHighlighter(highlighter, database);
            }

            // finish all open windows
            boolean nothingDone = true;
            for (EditWindow wnd : updatedWindows) {
                Highlighter highlighter = wnd.getHighlighter();

                // Something found to highlight
                if (highlighter != null) {
                    highlighter.finished();
                    if (User.isShiftWindowToErrors()) {
                        Rectangle2D bounds = highlighter.getHighlightedArea(wnd);
                        Rectangle2D displayBounds = wnd.displayableBounds();
                        if (wnd.isInPlaceEdit()) {
                            Point2D llPt = new Point2D.Double(displayBounds.getMinX(), displayBounds.getMinY());
                            Point2D urPt = new Point2D.Double(displayBounds.getMaxX(), displayBounds.getMaxY());
                            FixpTransform intoCell = wnd.getInPlaceTransformIn();
                            intoCell.transform(llPt, llPt);
                            intoCell.transform(urPt, urPt);
                            double lX = Math.min(llPt.getX(), urPt.getX());
                            double hX = Math.max(llPt.getX(), urPt.getX());
                            double lY = Math.min(llPt.getY(), urPt.getY());
                            double hY = Math.max(llPt.getY(), urPt.getY());
                            displayBounds = new Rectangle2D.Double(lX, lY, hX - lX, hY - lY);
                        }
                        if (bounds.getMinX() >= displayBounds.getMaxX()
                                || bounds.getMaxX() <= displayBounds.getMinX()
                                || bounds.getMinY() >= displayBounds.getMaxY()
                                || bounds.getMaxY() <= displayBounds.getMinY()) {
                            Rectangle2D newBounds = highlighter.getHighlightedArea(wnd);
                            double newLX = newBounds.getMinX() - newBounds.getWidth() / 2;
                            double newHX = newBounds.getMaxX() + newBounds.getWidth() / 2;
                            double newLY = newBounds.getMinY() - newBounds.getHeight() / 2;
                            double newHY = newBounds.getMaxY() + newBounds.getHeight() / 2;
                            newBounds.setRect(newLX, newLY, newHX - newLX, newHY - newLY);
                            wnd.focusScreen(newBounds);
                        } else {
                            highlighter.ensureHighlightingSeen(wnd.getWindowFrame());
                        }
                    } else {
                        highlighter.ensureHighlightingSeen(wnd.getWindowFrame());
                    }

//                    // make sure the selection is visible
//                    Rectangle2D hBounds = highlighter.getHighlightedArea(wnd);
//                    Rectangle2D shown = wnd.getDisplayedBounds();
//            		if (wnd.isInPlaceEdit())
//            		{
//            			Point2D llPt = new Point2D.Double(shown.getMinX(), shown.getMinY());
//            			Point2D urPt = new Point2D.Double(shown.getMaxX(), shown.getMaxY());
//            			AffineTransform intoCell = wnd.getInPlaceTransformIn();
//            			intoCell.transform(llPt, llPt);
//            			intoCell.transform(urPt, urPt);
//            			double lX = Math.min(llPt.getX(), urPt.getX());
//            			double hX = Math.max(llPt.getX(), urPt.getX());
//            			double lY = Math.min(llPt.getY(), urPt.getY());
//            			double hY = Math.max(llPt.getY(), urPt.getY());
//            			shown = new Rectangle2D.Double(lX, lY, hX-lX, hY-lY);
//            		}
//                    if (!shown.intersects(hBounds))
//                    {
//                        wnd.focusOnHighlighted();
//                    }
                }
                nothingDone = false;
            }

            if (nothingDone) {
                Cell logCell = log.getCell();
                // Checking also that the cell hasn't been removed yet
                if (logCell != null && logCell.isLinked()) {
                    // in case of errors or warnings with only cell information
                    // This would bring the EditWindow to the front.
                    EditWindow.showEditWindowForCell(logCell, null);
                }
            }
        }

        // return the error message
        return log.getMessageString();
    }

    /**
     * Method to show an error message.
     * @param message the error message to show.
     * @param title the title of a dialog with the error message.
     */
    public void showErrorMessage(final String message, final String title) {
        if (Job.isClientThread()) {
            JFrame wf = TopLevel.getCurrentJFrame(false);
            if (wf == null) {
                System.err.println(message);
            }
            JOptionPane.showMessageDialog(wf, breakLongMessage(message), title, JOptionPane.ERROR_MESSAGE);
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    showErrorMessage(message, title);
                }
            });
        }
    }

    private static final int WIDTHLIMIT = 100;
    private static final int LINELIMIT = 20;

    /**
     * Method to break long messages into multiple lines so they fit in a dialog.
     * @param message the original message
     * @return the message, broken into shorter lines.
     */
    private String breakLongMessage(String message)
    {
    	if (message.length() <= WIDTHLIMIT) return message;
    	StringBuffer sb = new StringBuffer();
    	int numberOfLines = 0;
    	while (message.length() > WIDTHLIMIT)
    	{
    		int blankPos = message.lastIndexOf(' ', WIDTHLIMIT);
    		if (blankPos < 0) blankPos = WIDTHLIMIT;
    		sb.append(message.substring(0, blankPos));
    		sb.append("\n");
    		if (message.charAt(blankPos) == ' ') blankPos++;
    		message = message.substring(blankPos);
    		numberOfLines++;
    		if (numberOfLines > LINELIMIT)
    			break; // reached limit of # of lines
    	}
    	// line limit not reached
    	if (numberOfLines < LINELIMIT)
    		sb.append(message);
    	else
    		sb.append(" and more ...");
    	return sb.toString();
    }

    /**
     * Method to show an informational message.
     * @param message the message to show.
     * @param title the title of a dialog with the message.
     */
    public void showInformationMessage(final String message, final String title) 
    {
    	showInformationMessage(TopLevel.getCurrentJFrame(), message, title);
    }

    /**
     * Method to show an informational message.
     * @param frame top window to use. It could be null
     * @param message the message to show.
     * @param title the title of a dialog with the message.
     */
    public void showInformationMessage(JFrame frame, final String message, final String title) {
        if (Job.isClientThread()) {
            JOptionPane.showMessageDialog(frame, breakLongMessage(message), title, JOptionPane.INFORMATION_MESSAGE);
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    showInformationMessage(message, title);
                }
            });
        }
    }
    
    /**
     * Method print a message.
     * @param message the message to show.
     * @param newLine add new line after the message
     */
    public void printMessage(String message, boolean newLine) {
        final String s = newLine ? message + "\n" : message;
        if (Job.isClientThread()) {
            appendString(s);
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    MessagesWindow.appendString(s);
                }
            });
        }
    }

    /**
     * Method to start saving messages.
     * @param filePath file to save
     */
    public void saveMessages(final String filePath) {
        if (!Job.isClientThread()) {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    saveMessages(filePath);
                }
            });
        }
        try {
            if (printWriter != null) {
                printWriter.close();
                printWriter = null;
            }
            if (filePath == null) {
                return;
            }
            printWriter = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
        } catch (IOException e) {
            System.err.println("Error creating " + filePath);
            System.out.println("Error creating " + filePath);
            return;
        }

        System.out.println("Messages will be saved to " + filePath);
    }
    private static PrintWriter printWriter = null;
    private static boolean newCommand = true;
    private static int commandNumber = 1;

    /**
     * Method to report that the user issued a new command (click, keystroke, pulldown menu).
     * The messages window separates output by command so that each command's results
     * can be distinguished from others.
     */
    public static void userCommandIssued() {
        newCommand = true;
    }

    public void appendString(String str) {
        if (str.length() == 0) {
            return;
        }

        if (newCommand) {
            newCommand = false;
            str = "=================================" + (commandNumber++) + "=================================\n" + str;
        }

        if (printWriter != null) {
            printWriter.print(str);
            printWriter.flush();
        }
        MessagesWindow.appendString(str);
    }

    /**
     * Method to show a message and ask for confirmation.
     * @param message the message to show.
     * @return true if "yes" was selected, false if "no" was selected.
     */
    public boolean confirmMessage(Object message) {
        int response = JOptionPane.showConfirmDialog(TopLevel.getCurrentJFrame(), breakLongMessage(message.toString()));
        return response == JOptionPane.YES_OPTION;
    }

    /**
     * Method to ask for a choice among possibilities.
     * @param message the message to show.
     * @param title the title of the dialog with the query.
     * @param choices an array of choices to present, each in a button.
     * @param defaultChoice the default choice.
     * @return the index into the choices array that was selected.
     */
    public int askForChoice(String message, String title, String[] choices, String defaultChoice) {
        // make sure the message is not too long and add \n if necessary
        String msg = message;
        int size = msg.length();
        int pos = 0;
        int lineNumber = 0;
        String newMsg = "";
        while (pos < size && lineNumber < 10) {
            int endIndex = pos + 256;
            if (endIndex > size) {
                endIndex = size;
            }
            newMsg += msg.substring(pos, endIndex);
            newMsg += "\n";
            pos += 256;
            lineNumber++;
        }
        if (pos < size) // too many lines
        {
            newMsg += "........\n";
            // adding the end of the message. If end of message is close then add the remainder otherwise
            // print the last 256 characters.
            int index = (size - pos > 256) ? (size - 256) : (pos);
            newMsg += msg.substring(index, size);
        }
        msg = newMsg;
        message = msg;
        int val = JOptionPane.showOptionDialog(TopLevel.getCurrentJFrame(), breakLongMessage(message), title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, defaultChoice);
        return val;
    }

    /**
     * Method to ask for a line of text.
     * @param message the prompt message.
     * @param title the title of a dialog with the message.
     * @param def the default response.
     * @return the string (null if canceled).
     */
    public String askForInput(Object message, String title, String def) {
        Object ret = JOptionPane.showInputDialog(TopLevel.getCurrentJFrame(), breakLongMessage(message.toString()), title, JOptionPane.QUESTION_MESSAGE, null, null, def);
        if (ret == null) {
            return null;
        }
        return ret.toString();
    }

    /** For Preference */
    public static void importPrefs(URL fileURL) {
        assert Job.isClientThread();
        if (fileURL == null) {
            return;
        }
        System.out.println("Importing preferences...");
        Pref.importPrefs(fileURL);
        Environment env = EDatabase.clientDatabase().getEnvironment();

        // Mirror Settings in Preferences
        env.saveToPreferences();

        // recache all preferences
        loadPreferences(env.techPool);
        TopLevel.getCurrentJFrame().getEMenuBar().restoreSavedBindings(false); //trying to cache again
        User.technologyChanged();
        WindowFrame.repaintAllWindows();
        System.out.println("...preferences imported from " + fileURL.getFile());
    }

    // ExtendedUserInterface
    /**
     * Save current state of highlights and return its ID.
     */
    @Override
    public int saveHighlights() {
        EditWindow_ wnd = getCurrentEditWindow_();
        if (wnd == null) {
            return -1;
        }

        SavedHighlights sh = new SavedHighlights(lastId++, wnd);
        while (savedHighlights.size() >= User.getMaxUndoHistory() && !savedHighlights.isEmpty()) {
            savedHighlights.remove(0);
        }
        savedHighlights.add(sh);
        return sh.id;
    }

    /**
     * Restore state of highlights by its ID.
     */
    @Override
    public void restoreHighlights(int highlightsId) {
        for (SavedHighlights sh : savedHighlights) {
            if (sh.id == highlightsId) {
                sh.restore();
                break;
            }
        }
    }

    /**
     * Free highlights of specified EditWindow
     * @param wnd specified EditWindow
     */
    public static void freeHighlights(EditWindow_ wnd) {
        for (int i = savedHighlights.size() - 1; i >= 0; i--) {
            if (savedHighlights.get(i).wnd == wnd) {
                savedHighlights.remove(i);
            }
        }
    }

    /**
     * Show status of undo/redo buttons
     * @param newUndoEnabled new status of undo button.
     * @param newRedoEnabled new status of redo button.
     */
    @Override
    public void showUndoRedoStatus(boolean newUndoEnabled, boolean newRedoEnabled) {
        PropertyChangeEvent e = null;
        if (undoEnabled != newUndoEnabled) {
            //           PropertyChangeEvent e = new PropertyChangeEvent(this, propUndoEnabled, undoEnabled, newUndoEnabled);
            undoEnabled = newUndoEnabled;
            SwingUtilities.invokeLater(new PropertyChangeRun(e));
        }
        if (redoEnabled != newRedoEnabled) {
            //           PropertyChangeEvent e = new PropertyChangeEvent(this, propRedoEnabled, redoEnabled, newRedoEnabled);
            redoEnabled = newRedoEnabled;
            SwingUtilities.invokeLater(new PropertyChangeRun(e));
        }
    }

    /**
     * Show new database snapshot.saveh
     * @param newSnapshot new snapshot.
     */
    public void showSnapshot(Snapshot newSnapshot, boolean undoRedo) {
        assert Job.isClientThread();
        DatabaseChangeEvent event = new DatabaseChangeEvent(currentSnapshot, newSnapshot);
        Snapshot oldSnapshot = currentSnapshot;
        EDatabase database = EDatabase.clientDatabase();
        database.lock(true);
        try {
//                database.checkFresh(oldSnapshot);
            database.lowLevelSetCanUndoing(true);
            try {
                database.undo(newSnapshot);
            } catch (Throwable e) {
                ActivityLogger.logException(e);
                database.recover(newSnapshot);
            }
            database.lowLevelSetCanUndoing(false);
        } finally {
            database.unlock();
            endChanging();
        }
        currentSnapshot = newSnapshot;
        if (newSnapshot.environment != oldSnapshot.environment) {
            Environment.setThreadEnvironment(newSnapshot.environment);
            if (newSnapshot.environment.toolSettings != oldSnapshot.environment.toolSettings) {
                ToolSettings.attachToGroup(newSnapshot.environment.toolSettings);
            }
            if (newSnapshot.techPool != oldSnapshot.techPool) {
                LayerVisibility.preserveVisibility();
                loadPreferences(newSnapshot.techPool);
                User.technologyChanged();
                WindowFrame.repaintAllWindows();
            }
        }
        for (Iterator<Listener> it = Tool.getListeners(); it.hasNext();) {
            Listener listener = it.next();
            listener.endBatch(oldSnapshot, newSnapshot, undoRedo);
        }
        fireDatabaseChangeEvent(event);
//        SwingUtilities.invokeLater(new DatabaseChangeRun(newSnapshot, undoRedo));
    }

    @Override
    public void beep() {
        if (Job.isClientThread()) {
            User.playSound();
//            Toolkit.getDefaultToolkit().beep();
        } else {
            SwingUtilities.invokeLater(new Runnable() {

                public void run() {
                    beep();
                }
            });
        }
    }

    /**
     * Method to tell whether undo can be done.
     * This is used by the tool bar to determine whether the undo button should be available.
     * @return true if undo can be done.
     */
    public static boolean getUndoEnabled() {
        return undoEnabled;
    }

    /**
     * Method to tell whether redo can be done.
     * This is used by the tool bar to determine whether the undo button should be available.
     * @return true if redo can be done.
     */
    public static boolean getRedoEnabled() {
        return redoEnabled;
    }

//	/** Add a property change listener. This generates Undo and Redo enabled property changes */
//	public static synchronized void addUndoRedoListener(PropertyChangeListener l)
//	{
//        assert SwingUtilities.isEventDispatchThread();
//		undoRedoListenerList.add(PropertyChangeListener.class, l);
//	}
//
//	/** Remove a property change listener. */
//	public static synchronized void removeUndoRedoListener(PropertyChangeListener l)
//	{
//        assert SwingUtilities.isEventDispatchThread();
//		undoRedoListenerList.remove(PropertyChangeListener.class, l);
//	}
    private static void firePropertyChange(PropertyChangeEvent e) {
        assert Job.isClientThread();
        ToolBar.updateUndoRedoButtons(getUndoEnabled(), getRedoEnabled());

        // Check all current WindowFrames and determine if displayed cells are still valid
        // close windows that reference this cell
        for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext();) {
            WindowFrame wf = it.next();
            WindowContent content = wf.getContent();
            if (content == null) {
                continue;
            }
            Cell c = content.getCell();
            if (c != null && !c.isLinked()) // got removed in undo
            {
                wf.setCellWindow(null, null);
                content.fullRepaint();
            }
        }
//        Object[] listeners;
//        synchronized (UserInterfaceMain.class) {
//            listeners = undoRedoListenerList.getListenerList();
//        }
//        // Process the listeners last to first, notifying those that are interested in this event
//        for (int i = listeners.length-2; i>=0; i-=2) {
//            if (listeners[i] == PropertyChangeListener.class)
//                ((PropertyChangeListener)listeners[i+1]).propertyChange(e);
//        }
    }

    private static class PropertyChangeRun implements Runnable {

        private PropertyChangeEvent e;

        private PropertyChangeRun(PropertyChangeEvent e) {
            this.e = e;
        }

        public void run() {
            firePropertyChange(e);
        }
    }

    /** Add a DatabaseChangeListener. It will be notified when
     * state of the database changes.
     * @param l the listener
     */
    public static synchronized void addDatabaseChangeListener(DatabaseChangeListener l) {
        listenerList.add(DatabaseChangeListener.class, l);
    }

    /** Remove a DatabaseChangeListener. */
    public static synchronized void removeDatabaseChangeListener(DatabaseChangeListener l) {
        listenerList.remove(DatabaseChangeListener.class, l);
    }

    /**
     * Fire DatabaseChangeEvent to DatabaseChangeListeners.
     * @param e DatabaseChangeEvent.
     */
    public static void fireDatabaseChangeEvent(DatabaseChangeEvent e) {
        Object[] listeners;
        synchronized (User.class) {
            listeners = listenerList.getListenerList();
        }
        // Process the listeners last to first, notifying those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == DatabaseChangeListener.class) {
                ((DatabaseChangeListener) listeners[i + 1]).databaseChanged(e);
            }
        }
    }

    private static void loadPreferences(TechPool techPool) {
        assert techPool == Environment.getThreadEnvironment().techPool;
        Pref.setCachedObjsFromPreferences();
        EditingPreferences.lowLevelSetThreadLocalEditingPreferences(lastSavedEp = new EditingPreferences(false, techPool));
        currentGraphicsPreferences = new GraphicsPreferences(false, techPool);
        EditWindow.setLayerVisibilityAll(LayerVisibility.loadPreferences());
        ClickZoomWireListener.theOne.readPrefs();
    }

    public static EditingPreferences getEditingPreferences() {
        assert Job.isClientThread();
        assert lastSavedEp == EditingPreferences.getInstance();
        return lastSavedEp;
    }
    private static EditingPreferences lastSavedEp;

    public static void setEditingPreferences(EditingPreferences newEp) {
        EditingPreferences oldEp = getEditingPreferences();
        if (newEp.equals(oldEp)) {
            return;
        }
        Pref.delayPrefFlushing();
        newEp.putPrefs(Pref.getPrefRoot(), true, lastSavedEp);
        EditingPreferences.lowLevelSetThreadLocalEditingPreferences(lastSavedEp = newEp);
        ClickZoomWireListener.theOne.readPrefs();
        Pref.resumePrefFlushing();
    }
    private static boolean badAccessReported = false;

    public static GraphicsPreferences getGraphicsPreferences() {
        if (!badAccessReported && !Job.isClientThread()) {
            String msg = "GraphicsPreferences is accessed from " + Job.getRunningJob();
            if (Job.getDebug()) {
                ActivityLogger.logMessage(msg);
                System.out.println(msg);
            }
            badAccessReported = true;
        }
        return currentGraphicsPreferences;
    }

    public static void setGraphicsPreferences(GraphicsPreferences gp) {
        assert Job.isClientThread();
        if (gp.equals(currentGraphicsPreferences)) {
            return;
        }
        Pref.delayPrefFlushing();
        gp.putPrefs(Pref.getPrefRoot(), true, currentGraphicsPreferences);
        Pref.resumePrefFlushing();
        currentGraphicsPreferences = gp;
    }
    private static int lastId = 0;
    private static ArrayList<SavedHighlights> savedHighlights = new ArrayList<SavedHighlights>();

    private static class SavedHighlights {

        /** id of this saved state */
        private final int id;
        /** EditWindow_ of highlights */
        private final EditWindow_ wnd;
        /** list of saved Highlights */
        private final List<Highlight> savedHighlights;
        /** saved Highlight offset */
        private final Point2D savedHighlightsOffset;

        private SavedHighlights(int id, EditWindow_ wnd) {
            this.id = id;
            this.wnd = wnd;
            savedHighlights = wnd.saveHighlightList();
            savedHighlightsOffset = wnd.getHighlightOffset();
        }

        private void restore() {
            wnd.restoreHighlightList(savedHighlights);
            wnd.setHighlightOffset((int) savedHighlightsOffset.getX(), (int) savedHighlightsOffset.getY());
            wnd.finishedHighlighting();
        }
    }

    /**
     * Class to display a Splash Screen at the start of the program.
     */
    private static class SplashWindow extends JFrame {

        public SplashWindow() {
            super();
            setUndecorated(true);
            setTitle("Electric Splash");
            setIconImage(TopLevel.getFrameIcon().getImage());

            JPanel whole = new JPanel();
            whole.setBorder(BorderFactory.createLineBorder(new Color(0, 170, 0), 5));
            whole.setLayout(new BorderLayout());

            ImageIcon splashImage = Resources.getResource(TopLevel.class, "SplashImage.gif");
            JLabel l = new JLabel(splashImage);
            whole.add(l, BorderLayout.CENTER);
            JLabel v = new JLabel("Version " + Version.getVersion(), JLabel.CENTER);
            whole.add(v, BorderLayout.SOUTH);
            String fontName = User.getFactoryDefaultFont();
            //String fontName = User.getDefaultFont();
            Font font = new Font(fontName, Font.BOLD, 24);
            v.setFont(font);
            v.setForeground(Color.BLACK);
            v.setBackground(Color.WHITE);

            getContentPane().add(whole, BorderLayout.SOUTH);

            pack();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension labelSize = getPreferredSize();
            setLocation(screenSize.width / 2 - (labelSize.width / 2),
                    screenSize.height / 2 - (labelSize.height / 2));
            addWindowListener(new WindowsEvents(this));
            setVisible(true);
            toFront();
            paint(getGraphics());
        }
    }

    /**
     * This class handles deactivation of the splash screen and forces it back to the top.
     */
    private static class WindowsEvents implements WindowListener {

        SplashWindow sw;

        WindowsEvents(SplashWindow sw) {
            super();
            this.sw = sw;
        }

        public void windowActivated(WindowEvent e) {
        }

        public void windowClosed(WindowEvent e) {
        }

        public void windowClosing(WindowEvent e) {
        }

        public void windowDeiconified(WindowEvent e) {
        }

        public void windowIconified(WindowEvent e) {
        }

        public void windowOpened(WindowEvent e) {
        }

        public void windowDeactivated(WindowEvent e) {
            TopLevel tl = TopLevel.getCurrentJFrame(false);
            Window w = e.getOppositeWindow();
            if (tl == w) {
                sw.toFront();
            }
        }
    }

//    private static void runThreadStatusTimer() {
//        int delay = 1000*60*10; // milliseconds
//        ElapseTimer timer = new ElapseTimer(delay, new ThreadStatusTask());
//        timer.start();
//    }
//
//    private static class ThreadStatusTask implements ActionListener {
//        public void actionPerformed(ActionEvent e) {
//            Thread t = Thread.currentThread();
//            ThreadGroup group = t.getThreadGroup();
//            // get the top level group
//            while (group.getParent() != null)
//                group = group.getParent();
//            Thread [] threads = new Thread[200];
//            int numThreads = group.enumerate(threads, true);
//            StringBuffer buf = new StringBuffer();
//            for (int i=0; i<numThreads; i++) {
//                buf.append("Thread["+i+"] "+threads[i]+": alive: "+threads[i].isAlive()+", interrupted: "+threads[i].isInterrupted()+"\n");
//            }
//            ActivityLogger.logThreadMessage(buf.toString());
//        }
//    }
    /**
     * Method to start the display of a progress dialog.
     * @param msg the message to show in the progress dialog.
     * @param filePath the file being read (null if not reading a file).
     */
    public void startProgressDialog(String msg, String filePath) {
        stopProgressDialog();
        try {
            String message;
            if (filePath == null) {
                message = msg + "...";
            } else {
                message = "Reading " + msg + " " + filePath + "...";
            }
            progress = new Progress(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
        progress.setProgress(0);
    }

    /**
     * Method to stop the progress bar
     */
    public void stopProgressDialog() {
        if (progress != null) {
            progress.close();
            progress = null;
        }
    }

    /**
     * Method to update the progress bar
     * @param pct the percentage done (from 0 to 100).
     */
    public void setProgressValue(int pct) {
        // progress is null if it is in quiet mode
        if (progress != null) {
            progress.setProgress(pct);
        }
    }

    /**
     * Method to set a text message in the progress dialog.
     * @param message the new progress message.
     */
    public void setProgressNote(String message) {
        // progress is null if it is in quiet mode
        if (progress != null) {
            progress.setNote(message);
        }
    }

    /**
     * Method to get text message in the progress dialog.
     * @return text message in the progress dialog.
     */
    public String getProgressNote() {
        if (progress == null) {
            return "";
        }
        return progress.getNote();
    }
}
