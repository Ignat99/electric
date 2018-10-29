/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ErrorLoggerTree.java
 *
 * Copyright (c) 2006, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.ui;

import com.sun.electric.database.change.DatabaseChangeEvent;
import com.sun.electric.database.change.DatabaseChangeListener;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.id.CellId;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.user.ErrorHighlight;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.ErrorLogger.MessageLog;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.util.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * Class to define a collection of highlighted errors in the Explorer tree.
 */
// ----------------------------- Explorer Tree Stuff ---------------------------

public class ErrorLoggerTree {

    public static final String errorNode = "ERRORS";
    /** Top of error tree */            private static final DefaultMutableTreeNode errorTree = new DefaultMutableTreeNode(errorNode);
    /** Path to error tree */           private static final TreePath errorPath = (new TreePath(ExplorerTreeModel.rootNode)).pathByAddingChild(errorTree);
    /** Current Logger */               static DefaultMutableTreeNode currentLogger;

    private static final ErrorLogger networkErrorLogger = ErrorLogger.newInstance("Network Errors");
//    private static final ErrorLogger drcErrorLogger = ErrorLogger.newInstance("DRC (incremental)");
    private static DefaultMutableTreeNode networkTree;
    private static DefaultMutableTreeNode drcTree;

    // public methods called from any thread

    public static boolean hasLogger(ErrorLogger logger) {
        return indexOf(logger) >= 0;
    };

    public static void addLogger(ErrorLogger logger, boolean explain, boolean terminate) {
        logger.termLogging_(terminate);
        if (logger.getNumLogs() == 0) return;
        SwingUtilities.invokeLater(new AddLogger(logger, explain));
    };

    public static void updateNetworkErrors(Cell cell, List<ErrorLogger.MessageLog> errors) {
        SwingUtilities.invokeLater(new UpdateNetwork(cell.getId(), errors));
    }

    public static void updateDrcErrors(Cell cell, List<ErrorLogger.MessageLog> newErrors, List<MessageLog> delErrors) {
        SwingUtilities.invokeLater(new UpdateDrc(cell.getId(), newErrors, delErrors));
    }

    // public methods called from GUI thread

    public static DefaultMutableTreeNode getExplorerTree() { return errorTree; }

   /**
     * Method to advance to the next error and report it.
     */
    public static void reportSingleGeometry(boolean separateWindow)
   {
       if (currentLogger == null)
       {
           System.out.println("No errors to report");
           return;
       }
        ((ErrorLoggerTreeNode)currentLogger.getUserObject()).reportSingleGeometry_(true, separateWindow);
    }

    /**
     * Method to advance to the next error and report it.
     * @param separateWindow true to show each cell in its own window; false to show in the current window.
     */
    public static String reportNextMessage(boolean separateWindow) {
        if (currentLogger == null) return "No errors to report";
        return ((ErrorLoggerTreeNode)currentLogger.getUserObject()).reportNextMessage_(true, separateWindow);
    }

    /**
     * Method to back up to the previous error and report it.
     * @param separateWindow true to show each cell in its own window; false to show in the current window.
     */
    public static String reportPrevMessage(boolean separateWindow) {
        if (currentLogger == null) return "No errors to report";
        return ((ErrorLoggerTreeNode)currentLogger.getUserObject()).reportPrevMessage_(separateWindow);
    }

    /**
     * Method to show the current collection of errors.
     */
    public static void showCurrentErrors()
    {
    	WindowFrame wf = WindowFrame.getCurrentWindowFrame();
    	if (wf == null) return;
        if (currentLogger == null) return;

		Job.getUserInterface().getCurrentEditWindow_().clearHighlighting();
        ErrorLoggerTreeNode node = (ErrorLoggerTreeNode)ErrorLoggerTree.currentLogger.getUserObject();
		int index = indexOf(node);
		highlightLogger(index, -1);
    	Job.getUserInterface().getCurrentEditWindow_().finishedHighlighting();
    }

    private static class AddLogger implements Runnable {
        private ErrorLogger logger;
        private boolean explain;
        AddLogger(ErrorLogger logger, boolean explain) {
            this.logger = logger;
            this.explain = explain;
        }
        public void run() {
            int i = indexOf(logger);
            if (i >= 0) {
                updateTree((DefaultMutableTreeNode)errorTree.getChildAt(i));
            } else {
                addLogger(errorTree.getChildCount(), logger);
                if (explain)
                    explain(logger);
            }
        }
    }

    private static void explain(ErrorLogger logger) {
        // To print consistent message in message window
        String extraMsg = "errors/warnings";
        if (logger.getNumErrors() == 0) extraMsg = "warnings";
        else  if (logger.getNumWarnings() == 0) extraMsg = "errors";
        String msg = logger.getInfo();
        System.out.println(msg);
        if (logger.getNumLogs() > 0) {
            System.out.println("Type > and < to step through " + extraMsg + ", or open the ERRORS view in the explorer");
        }
        if (logger.getNumErrors() > 0 && !logger.isPopupsDisabled()) {
            JOptionPane.showMessageDialog(TopLevel.getCurrentJFrame(), msg,
                logger.getSystem() + " finished with Errors", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static class UpdateNetwork implements Runnable {
        private CellId cellId;
        private List<ErrorLogger.MessageLog> errors;
        UpdateNetwork(CellId cellId, List<ErrorLogger.MessageLog> errors) {
            this.cellId = cellId;
            this.errors = new ArrayList<ErrorLogger.MessageLog>(errors);
        }
        public void run() {
            Cell cell = EDatabase.clientDatabase().getCell(cellId);
            if (cell == null) return;
            boolean changed = networkErrorLogger.clearLogs(cell) || !errors.isEmpty();
            networkErrorLogger.addMessages(errors);
            if (!changed) return;
            networkErrorLogger.termLogging_(true);
            if (networkErrorLogger.getNumLogs() == 0) {
                removeLogger(0);
                return;
            }
            if (networkTree == null)
                networkTree = addLogger(0, networkErrorLogger);
            updateTree(networkTree);
            setCurrent(0);
        }
    }

    private static class UpdateDrc implements Runnable {
        private CellId cellId;
        private List<ErrorLogger.MessageLog> newErrors;
        private List<ErrorLogger.MessageLog> delErrors;
        UpdateDrc(CellId cId, List<ErrorLogger.MessageLog> newErrs, List<MessageLog> delErrs) {
            this.cellId = cId;
            if (newErrs != null)
                this.newErrors = new ArrayList<ErrorLogger.MessageLog>(newErrs);
            if (delErrs != null)
                this.delErrors = new ArrayList<ErrorLogger.MessageLog>(delErrs);
        }
        public void run() {
            Cell cell = EDatabase.clientDatabase().getCell(cellId);
            if (cell == null) return;
            ErrorLogger drcErrorLogger = DRC.getDRCIncrementalLogger();
//            boolean changed = drcErrorLogger.clearLogs(cell) || (newErrors != null && !newErrors.isEmpty() ||
//                (delErrors != null && !delErrors.isEmpty()));
            drcErrorLogger.addMessages(newErrors);
            drcErrorLogger.deleteMessages(delErrors);
//            if (!changed) return;
            drcErrorLogger.termLogging_(true);
            int index = networkTree != null ? 1 : 0;
            if (drcErrorLogger.getNumLogs() == 0)
            {
                if (drcTree != null)
                    removeLogger(errorTree.getIndex(drcTree));
                return;
            }
            if (drcTree == null)
                drcTree = addLogger(index, drcErrorLogger);
            updateTree(drcTree);
            setCurrent(index);
        }
    }

    private static DefaultMutableTreeNode addLogger(int index, ErrorLogger logger) {
        ErrorLoggerTreeNode tn = new ErrorLoggerTreeNode(logger);
        UserInterfaceMain.addDatabaseChangeListener(tn);
        DefaultMutableTreeNode newNode = new ErrorLoggerDefaultMutableTreeNode(tn);
        int[] childIndices = new int[] { index };
        DefaultMutableTreeNode[] children = new DefaultMutableTreeNode[] { newNode };
        setCurrent(-1);
        errorTree.insert(newNode, index);
        currentLogger = newNode;
        ExplorerTreeModel.fireTreeNodesInserted(errorTree, errorPath, childIndices, children);
        updateTree(newNode);
        return newNode;
    }

    private static void removeLogger(int index) {
        if (errorTree.getChildCount() <= index)
            return; // nothing to remove. Case of incremental errors

        DefaultMutableTreeNode node = (DefaultMutableTreeNode)errorTree.getChildAt(index);
        ErrorLoggerTreeNode treeNode = (ErrorLoggerTreeNode)node.getUserObject();
        UserInterfaceMain.removeDatabaseChangeListener(treeNode);
        ErrorLogger drcErrorLogger = DRC.getDRCIncrementalLogger();

        // Clean DRC incremental logger
        if (treeNode.getLogger() == drcErrorLogger)
        {
            drcErrorLogger.clearAllLogs();
        }
        if (node == networkTree) networkTree = null;
        if (node == drcTree) drcTree = null;
        if (node == currentLogger) currentLogger = null;
        int[] childIndices = new int[] { index };
        DefaultMutableTreeNode[] children = new DefaultMutableTreeNode[] { node };
        errorTree.remove(index);
        ExplorerTreeModel.fireTreeNodesRemoved(errorTree, errorPath, childIndices, children);
    }

    private static void highlightLogger(int index, int sortKey)
    {
    	EditWindow ew = EditWindow.getCurrent();
    	if (ew == null) return;
    	Highlighter h = ew.getHighlighter();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)errorTree.getChildAt(index);
        ErrorLoggerTreeNode eltn = (ErrorLoggerTreeNode)node.getUserObject();
        ErrorLogger el = eltn.getLogger();
        EDatabase database = EDatabase.clientDatabase();
        for(int i=0; i<el.getNumLogs(); i++)
        {
        	MessageLog ml = el.getLog(i);
        	if (sortKey >= 0 && ml.getSortKey() != sortKey) continue;
        	for(Iterator<ErrorHighlight> it = ml.getHighlights(); it.hasNext(); )
        	{
        		ErrorHighlight eh = it.next();
        		eh.addToHighlighter(h, database);
        	}
        }
    }

    private static void updateTree(DefaultMutableTreeNode loggerNode) {
        TreePath loggerPath = errorPath.pathByAddingChild(loggerNode);
        int oldChildCount = loggerNode.getChildCount();
        if (oldChildCount != 0) {
            int[] childIndex = new int[oldChildCount];
            DefaultMutableTreeNode[] children = new DefaultMutableTreeNode[oldChildCount];
            for (int i = 0; i < oldChildCount; i++) {
                childIndex[i] = i;
                children[i] = (DefaultMutableTreeNode)loggerNode.getChildAt(i);
            }
            loggerNode.removeAllChildren();
            ExplorerTreeModel.fireTreeNodesRemoved(errorTree, loggerPath, childIndex, children);
        }
        ErrorLoggerTreeNode eltn = (ErrorLoggerTreeNode)loggerNode.getUserObject();
        ErrorLogger logger = eltn.logger;
        if (logger.getNumLogs() == 0) return;
        Map<Integer,DefaultMutableTreeNode> sortKeyMap = new HashMap<Integer,DefaultMutableTreeNode>();
        Map<Integer,String> sortKeyGroupNamesMap = logger.getSortKeyToGroupNames();
        // Extra level for loggers
        if (sortKeyGroupNamesMap != null) {
            for (Map.Entry e : sortKeyGroupNamesMap.entrySet())
            {
                String name = (String)e.getValue();
                Integer key = (Integer)e.getKey();
                DefaultMutableTreeNode grpNode = new DefaultMutableTreeNode(new ErrorLoggerGroupNode(name, key.intValue(), eltn));
                loggerNode.add(grpNode);
                sortKeyMap.put(key, grpNode);
            }
        }
        for (Iterator<ErrorLogger.MessageLog> it = logger.getLogs(); it.hasNext();) {
            ErrorLogger.MessageLog el = it.next();
            // by default, groupNode is entire loggerNode
            // but, groupNode could be sub-node:
            DefaultMutableTreeNode groupNode = loggerNode;
            if (logger.getSortKeyToGroupNames() != null)
            {
                groupNode = sortKeyMap.get(new Integer(el.getSortKey()));
                if (groupNode == null) // not found, put in loggerNode
                   groupNode = loggerNode;
            }
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(el);
            groupNode.add(node);
        }
        int newChildCount = loggerNode.getChildCount();
        int[] childIndex = new int[newChildCount];
        DefaultMutableTreeNode[] children = new DefaultMutableTreeNode[newChildCount];
        for (int i = 0; i < newChildCount; i++) {
            childIndex[i] = i;
            children[i] = (DefaultMutableTreeNode)loggerNode.getChildAt(i);
        }
        ExplorerTreeModel.fireTreeNodesInserted(errorTree, loggerPath, childIndex, children);
    }

    private static void setCurrent(int index) {
        int oldIndex = currentLogger != null ? indexOf((ErrorLoggerTreeNode)currentLogger.getUserObject()) : -1;
        if (index == oldIndex) return;
        currentLogger = index >= 0 ? (DefaultMutableTreeNode)errorTree.getChildAt(index) : null;
        int l = 0;
        if (oldIndex >= 0) l++;
        if (index >= 0) l++;
        int[] childIndex = new int[l];
        TreeNode[] children = new TreeNode[l];
        l = 0;
        if (oldIndex >= 0 && oldIndex < index) {
            childIndex[l] = oldIndex;
            children[l] = errorTree.getChildAt(oldIndex);
            l++;
        }
        if (index >= 0) {
            childIndex[l] = index;
            children[l] = errorTree.getChildAt(index);
            l++;
        }
        if (oldIndex >= 0 && oldIndex > index) {
            childIndex[l] = oldIndex;
            children[l] = errorTree.getChildAt(oldIndex);
            l++;
        }
        ExplorerTreeModel.fireTreeNodesChanged(errorTree, errorPath, childIndex, children);
    }

    /** Delete this logger */
//    private static void delete(ErrorLoggerTreeNode node) {
//        int index = indexOf(node);
//        if (index < 0) return;
//        removeLogger(index);
//        if (currentLogger != null && ((ErrorLoggerTreeNode)currentLogger.getUserObject()) == node) {
//            if (errorTree.getChildCount() != 0)
//                currentLogger = (DefaultMutableTreeNode)errorTree.getChildAt(0);
//            else
//                currentLogger = null;
//        }
//    }

    private static int indexOf(ErrorLoggerTreeNode tn) {
        for (int i = 0, numLoggers = errorTree.getChildCount(); i < numLoggers; i++)
            if (((DefaultMutableTreeNode)errorTree.getChildAt(i)).getUserObject() == tn) return i;
        return -1;
    }

    private static int indexOf(ErrorLogger logger) {
        for (int i = 0, numLoggers = errorTree.getChildCount(); i < numLoggers; i++) {
            DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode)errorTree.getChildAt(i);
            ErrorLoggerTreeNode errorLoggerTreeNode = (ErrorLoggerTreeNode)defaultMutableTreeNode.getUserObject();
            if (errorLoggerTreeNode.logger == logger) return i;
        }
        return -1;
    }

    public static void importLogger()
    {
        String fileName = OpenFile.chooseInputFile(FileType.XML, "Read ErrorLogger", null);
        if (fileName == null) return; // nothing to load

        try {
            ErrorLogger.XMLParser parser = new ErrorLogger.XMLParser();
            ErrorLogger logger = parser.process(TextUtils.makeURLToFile(fileName), true);
            addLogger(logger, false, true);
        } catch (Exception e)
		{
			System.out.println("Error loading " + fileName);
			return;
		}
    }

    public static class ErrorLoggerDefaultMutableTreeNode extends DefaultMutableTreeNode {
        ErrorLoggerDefaultMutableTreeNode(ErrorLoggerTreeNode tn) { super(tn); }
        public boolean isLeaf() { return false; }
    }

    public static class ErrorLoggerGroupNode
    {
    	private String name;
    	private int sortKey;
    	private ErrorLoggerTreeNode parent;

    	ErrorLoggerGroupNode(String s, int k, ErrorLoggerTreeNode p)
    	{
    		name = s;
    		sortKey = k;
    		parent = p;
    	}

    	public int getSortKey() { return sortKey; }

    	public ErrorLoggerTreeNode getParentNode() { return parent; }

    	public boolean equals(Object x)
    	{
    		if (x instanceof ErrorLoggerGroupNode)
    		{
    			ErrorLoggerGroupNode elgn = (ErrorLoggerGroupNode)x;
    			if (elgn.name.equals(name) && elgn.parent == parent) return true;
    		}
    		return false;
    	}

    	public String toString() { return name; }
    }

    public static class ErrorLoggerTreeNode implements DatabaseChangeListener //, ActionListener
    {
        private ErrorLogger logger;
        private int currentLogNumber = -1;
        private ErrorLogger.MessageLog currentMsgLog;
        private int currentMsgLogGeoIndex;

        ErrorLoggerTreeNode(ErrorLogger log)
        {
            this.logger = log;
        }

        public void setLogNumber(int number)
        {
            currentLogNumber = number;
            currentMsgLog = logger.getLog(number);
        }

        public ErrorLogger getLogger() { return logger; }

        public String toString() { return "ErrorLogger Information: " +  logger.getInfo();}

        private int getNextMessageNumber()
        {
            int  currentLogNumber = this.currentLogNumber;

            if (currentLogNumber < logger.getNumLogs()-1) {
                currentLogNumber++;
            } else {
                if (logger.getNumLogs() <= 0) return -1; //"No "+logger.getSystem()+" errors";
                currentLogNumber = 0;
            }
            return currentLogNumber;
        }

        private void reportSingleGeometry_(boolean showHigh, boolean separateWindow)
        {
            if (currentMsgLog == null)
            {
                currentLogNumber = getNextMessageNumber();
                if (currentLogNumber < 0) return; // nothing to show.
                currentMsgLog = logger.getLog(currentLogNumber);
                currentMsgLogGeoIndex = 0;
            }
            Job.getUserInterface().reportLog(currentMsgLog, showHigh, separateWindow, currentMsgLogGeoIndex);
            currentMsgLogGeoIndex = (currentMsgLogGeoIndex < currentMsgLog.getNumHighlights() - 1) ?
                currentMsgLogGeoIndex+1 : 0;
        }

        private String reportNextMessage_(boolean showHigh, boolean separateWindow)
        {
            currentLogNumber = getNextMessageNumber();
            if (currentLogNumber < 0)
                return "No "+logger.getSystem()+" errors";
            return reportLog(currentLogNumber, showHigh, separateWindow);
        }

        private String reportPrevMessage_(boolean separateWindow) {
            if (currentLogNumber > 0) {
                currentLogNumber--;
            } else {
                if (logger.getNumLogs() <= 0) return "No "+logger.getSystem()+" errors";
                currentLogNumber = logger.getNumLogs() - 1;
            }
            return reportLog(currentLogNumber, true, separateWindow);
        }

        /**
         * Report an error
         */
        private String reportLog(int logNumber, boolean showHigh, boolean separateWindow) {

            if (logNumber < 0 || (logNumber >= logger.getNumLogs())) {
                return logger.getSystem() + ": no such error or warning "+(logNumber+1)+", only "+logger.getNumLogs()+" errors.";
            }

            currentMsgLog = logger.getLog(logNumber);
            currentMsgLogGeoIndex = 0;
            String extraMsg = null;
            if (logNumber < logger.getNumErrors()) {
                extraMsg = " error " + (logNumber+1) + " of " + logger.getNumErrors();
            } else {
                extraMsg = " warning " + (logNumber+1-logger.getNumErrors()) + " of " + logger.getNumWarnings();
            }
            String message = Job.getUserInterface().reportLog(currentMsgLog, showHigh, separateWindow, -1);
            return (logger.getSystem() + extraMsg + ": " + message);
        }

        public void databaseChanged(DatabaseChangeEvent e) {
            // check if any errors need to be deleted
            boolean changed = false;
            for (int i = logger.getNumLogs() - 1; i >= 0; i--) {
                MessageLog err = logger.getLog(i);
                if (!err.isValid(EDatabase.clientDatabase())) {
                    logger.deleteLog(i);
                    if (i < currentLogNumber)
                        currentLogNumber--;
                    else if (i == currentLogNumber)
                        currentLogNumber = 0;
                    changed = true;
                }
            }
            if (!changed) return;
            int index = indexOf(this);
            if (index < 0) return;
            if (logger.getNumLogs() == 0)
                removeLogger(index);
            else
            {
        		// remember the state of the tree
            	WindowFrame wf = WindowFrame.getCurrentWindowFrame();
            	if (wf != null)
            	{
	            	ExplorerTree tree = wf.getExplorerTab();
	            	ExplorerTreeModel etm = tree.model();
	            	ExplorerTree.KeepTreeExpansion kte = new ExplorerTree.KeepTreeExpansion(tree, etm.getRoot(), etm, errorPath);
	                updateTree((DefaultMutableTreeNode)errorTree.getChildAt(index));
	                kte.restore();
            	}
            }
        }
    }

    public static void deleteAllLoggers() {
        for (int i = errorTree.getChildCount() - 1; i >= 0; i--)
            removeLogger(i);
    }

    public static void deleteLogger(ExplorerTree ex)
    {                                             
        TreePath [] paths = ex.getSelectionPaths();
        for(int i=0; i<paths.length; i++)
        {
            Object obj = paths[i].getLastPathComponent();
            if (obj instanceof DefaultMutableTreeNode)
            {
                Object clickedObject = ((DefaultMutableTreeNode)obj).getUserObject();
                if (clickedObject instanceof ErrorLoggerTreeNode)
                {
                    int index = indexOf((ErrorLoggerTreeNode)clickedObject);
                    removeLogger(index);
                }
            }
        }
    }

    public static void exportLogger(ErrorLoggerTreeNode node)
    {
        ErrorLogger logger = node.getLogger();
        String filePath = null;
        try
        {
            filePath = OpenFile.chooseOutputFile(FileType.XML, null, "ErrorLoggerSave.xml");
            if (filePath == null) return; // cancel operation
            logger.exportErrorLogger(filePath);
        } catch (Exception se)
        {
            if (Job.getDebug())
                se.printStackTrace();
            System.out.println("Error creating " + filePath);
        }
    }
    
    public static void showAllLogger(ExplorerTree ex)
    {  	
        Job.getUserInterface().getCurrentEditWindow_().clearHighlighting();
        TreePath [] paths = ex.getSelectionPaths();
        for(int i=0; i<paths.length; i++)
        {
            Object obj = paths[i].getLastPathComponent();
            if (obj instanceof DefaultMutableTreeNode)
            {
                Object clickedObject = ((DefaultMutableTreeNode)obj).getUserObject();
                if (clickedObject instanceof ErrorLoggerTreeNode)
                {
                    int index = indexOf((ErrorLoggerTreeNode)clickedObject);
                    highlightLogger(index, -1);
                } else if (clickedObject instanceof ErrorLoggerGroupNode)
                {
                	ErrorLoggerGroupNode egn = (ErrorLoggerGroupNode)clickedObject;
                	int sortKey = egn.getSortKey();
                    int index = indexOf(egn.getParentNode());
                    highlightLogger(index, sortKey);
                } else if (clickedObject instanceof ErrorLogger.MessageLog)
                {
                	ErrorLogger.MessageLog ml = (ErrorLogger.MessageLog)clickedObject;
                	EditWindow ew = EditWindow.getCurrent();
                	if (ew == null) return;
                	Highlighter h = ew.getHighlighter();
                    EDatabase database = EDatabase.clientDatabase();
                    for(Iterator<ErrorHighlight> it = ml.getHighlights(); it.hasNext(); )
                	{
                		ErrorHighlight eh = it.next();
                		eh.addToHighlighter(h, database);
                	}
                }
            }
        }
        Job.getUserInterface().getCurrentEditWindow_().finishedHighlighting();
    }

    public static void setCurrentLogger(ErrorLoggerTreeNode node)
    {
        int index = indexOf(node);
        setCurrent(index);
    }
}
