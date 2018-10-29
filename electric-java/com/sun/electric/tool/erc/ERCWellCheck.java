/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ERCWellCheck.java
 *
 * Copyright (c) 2004, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.erc;

import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.text.PrefPackage;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.topology.RTBounds;
import com.sun.electric.database.topology.RTNode;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.ArcProto;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitiveNode.Function;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.NodeLayer;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.erc.wellcheck.ConnectionCheck;
import com.sun.electric.tool.erc.wellcheck.DRCCheck;
import com.sun.electric.tool.erc.wellcheck.DistanceCheck;
import com.sun.electric.tool.erc.wellcheck.NetValues;
import com.sun.electric.tool.erc.wellcheck.OnRailCheck;
import com.sun.electric.tool.erc.wellcheck.ShortCircuitCheck;
import com.sun.electric.tool.erc.wellcheck.Utils;
import com.sun.electric.tool.erc.wellcheck.Utils.WorkDistributionStrategy;
import com.sun.electric.tool.erc.wellcheck.WellCheckAnalysisStrategy;
import com.sun.electric.tool.erc.wellcheck.WellCon;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.dialogs.EModelessDialog;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.util.concurrent.Parallel;
import com.sun.electric.tool.util.concurrent.exceptions.PoolExistsException;
import com.sun.electric.tool.util.concurrent.patterns.PForTask;
import com.sun.electric.tool.util.concurrent.patterns.PJob;
import com.sun.electric.tool.util.concurrent.patterns.PTask;
import com.sun.electric.tool.util.concurrent.runtime.Scheduler.UnknownSchedulerException;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.IThreadPool;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPool;
import com.sun.electric.tool.util.concurrent.runtime.taskParallel.ThreadPoolJdkForkJoin;
import com.sun.electric.tool.util.concurrent.utils.BlockedRange1D;
import com.sun.electric.util.CollectionFactory;
import com.sun.electric.util.ElapseTimer;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.math.DBMath;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * This is the Electrical Rule Checker tool.
 */
public class ERCWellCheck {
    private static final boolean SCALA_FORKJOIN = true;
    private static final boolean SIMPLE_SPREAD = true;

    private Cell cell;
    private Set<Object> possiblePrimitives;
    private List<WellCon> wellCons = new ArrayList<WellCon>();
    private Iterator<WellCon>[] wellConIterator;
    private List<WellCon>[] wellConLists;
    private RTNode<WellBound> pWellRoot, nWellRoot;
    private int pWellCount, nWellCount;
    private Layer pWellLayer, nWellLayer;
    private ErrorLogger errorLogger;
    private WellCheckJob job;
    private double worstPWellDist;
    private Point2D worstPWellCon;
    private Point2D worstPWellEdge;
    private double worstNWellDist;
    private Point2D worstNWellCon;
    private Point2D worstNWellEdge;
    private WellCheckPreferences wellPrefs;
    private Map<Integer, List<Transistor>> transistors;
    private Set<Integer> networkExportAvailable;
    private boolean hasPCon;
    private boolean hasNCon;
    private IThreadPool threadPool;

    public static class WellCheckPreferences extends PrefPackage {
        private static final String PREF_NODE = "tool/erc";

        /**
         * Whether ERC should do well analysis using multiple processors. The
         * default is "true".
         */
        @BooleanPref(node = PREF_NODE, key = "ParallelWellAnalysis", factory = true)
        public boolean parallelWellAnalysis;

        /**
         * The number of processors to use in ERC well analysis. The default is
         * "0" (as many as there are).
         */
        @IntegerPref(node = PREF_NODE, key = "WellAnalysisNumProc", factory = 0)
        public int maxProc;

        /**
         * Whether ERC should check that all P-Well contacts connect to ground.
         * The default is "true".
         */
        @BooleanPref(node = PREF_NODE, key = "MustConnectPWellToGround", factory = true)
        public boolean mustConnectPWellToGround;

        /**
         * Whether ERC should check that all N-Well contacts connect to power.
         * The default is "true".
         */
        @BooleanPref(node = PREF_NODE, key = "MustConnectNWellToPower", factory = true)
        public boolean mustConnectNWellToPower;

        /**
         * How much P-Well contact checking the ERC should do. The values are:
         * <UL>
         * <LI>0: must have a contact in every well area.</LI>
         * <LI>1: must have at least one contact.</LI>
         * <LI>2: do not check for contact presence.</LI>
         * </UL>
         * The default is "0".
         */
        @IntegerPref(node = PREF_NODE, key = "PWellCheck", factory = 0)
        public int pWellCheck;

        /**
         * How much N-Well contact checking the ERC should do. The values are:
         * <UL>
         * <LI>0: must have a contact in every well area.</LI>
         * <LI>1: must have at least one contact.</LI>
         * <LI>2: do not check for contact presence.</LI>
         * </UL>
         * The default is "0".
         */
        @IntegerPref(node = PREF_NODE, key = "NWellCheck", factory = 0)
        public int nWellCheck;

        /**
         * Whether ERC should check DRC Spacing condition. The default is
         * "false".
         */
        @BooleanPref(node = PREF_NODE, key = "DRCCheckInERC", factory = false)
        public boolean drcCheck;

        /**
         * Whether ERC should find the contact that is farthest from the well
         * edge. The default is "false".
         */
        @BooleanPref(node = PREF_NODE, key = "FindWorstCaseWell", factory = false)
        public boolean findWorstCaseWell;

        /** true to prevent popups at the end of a run */
        public boolean disablePopups = false;

        public WellCheckPreferences(boolean factory) {
            super(factory);
        }
    }

    public enum WellType {
        none("none"), nwell("N"), pwell("P");

        private String name;

        private WellType(String name) {
            this.name = name;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return this.name;
        }
    }

    public static void analyzeCurCell() {
        UserInterface ui = Job.getUserInterface();
        Cell curCell = ui.needCurrentCell();
        if (curCell == null)
            return;

        View view = curCell.getView();
        if (view.isTextView() || view == View.SCHEMATIC || view == View.ICON) {
            System.out.println("Sorry, Well checking runs only on layout cells");
            return;
        }
        new WellCheckJob(curCell, new WellCheckPreferences(false));
    }

    public static int checkERCWell(Cell cell, WellCheckPreferences wellPrefs) {
        ERCWellCheck check = new ERCWellCheck(cell, null, wellPrefs);
        return check.runNow();
    }

    private ERCWellCheck(Cell cell, WellCheckJob job, WellCheckPreferences wellPrefs) {
        this.job = job;
        this.cell = cell;
        this.wellPrefs = wellPrefs;
        this.transistors = new HashMap<Integer, List<Transistor>>();
    }

    private static class WellCheckJob extends Job {
        private Cell cell;
        private double worstPWellDist, worstNWellDist;
        private EPoint worstPWellCon, worstPWellEdge;
        private EPoint worstNWellCon, worstNWellEdge;
        private WellCheckPreferences wellPrefs;

        private WellCheckJob(Cell cell, WellCheckPreferences wellPrefs) {
            super("ERC Well Check on " + cell, ERC.tool, Job.Type.SERVER_EXAMINE, null, null,
                    Job.Priority.USER);
            this.cell = cell;
            this.wellPrefs = wellPrefs;
            startJob();
        }

        public boolean doIt() throws JobException {
            ERCWellCheck check = new ERCWellCheck(cell, this, wellPrefs);
            check.runNow();
            worstPWellDist = check.worstPWellDist;
            fieldVariableChanged("worstPWellDist");
            worstNWellDist = check.worstNWellDist;
            fieldVariableChanged("worstNWellDist");
            if (check.worstPWellCon != null) {
                worstPWellCon = EPoint.fromLambda(check.worstPWellCon.getX(), check.worstPWellCon.getY());
                fieldVariableChanged("worstPWellCon");
            }
            if (check.worstPWellEdge != null) {
                worstPWellEdge = EPoint.fromLambda(check.worstPWellEdge.getX(), check.worstPWellEdge.getY());
                fieldVariableChanged("worstPWellEdge");
            }
            if (check.worstNWellCon != null) {
                worstNWellCon = EPoint.fromLambda(check.worstNWellCon.getX(), check.worstNWellCon.getY());
                fieldVariableChanged("worstNWellCon");
            }
            if (check.worstNWellEdge != null) {
                worstNWellEdge = EPoint.fromLambda(check.worstNWellEdge.getX(), check.worstNWellEdge.getY());
                fieldVariableChanged("worstNWellEdge");
            }
            return true;
        }

        public void terminateOK() {
            // show the farthest distance from a well contact
            UserInterface ui = Job.getUserInterface();
            EditWindow_ wnd = ui.getCurrentEditWindow_();
            if (wnd != null && (worstPWellDist > 0 || worstNWellDist > 0)) {
                wnd.clearHighlighting();
                if (worstPWellDist > 0) {
                    wnd.addHighlightLine(worstPWellCon, worstPWellEdge, cell, false, false);
                    System.out.println("Farthest distance from a P-Well contact is " + worstPWellDist);
                }
                if (worstNWellDist > 0) {
                    wnd.addHighlightLine(worstNWellCon, worstNWellEdge, cell, false, false);
                    System.out.println("Farthest distance from an N-Well contact is " + worstNWellDist);
                }
                wnd.finishedHighlighting();
            }
        }
    }

    private int runNow() {
        System.out.println("Checking Wells and Substrates in '" + cell.libDescribe() + "' ...");
        ElapseTimer timer = ElapseTimer.createInstance().start();
        errorLogger = ErrorLogger.newInstance("ERC Well Check ");
        initStatistics();

        // make a list of primitives that need to be examined
        possiblePrimitives = new HashSet<Object>();
        for (Iterator<Technology> it = Technology.getTechnologies(); it.hasNext();) {
            Technology tech = it.next();
            for (Iterator<PrimitiveNode> pIt = tech.getNodes(); pIt.hasNext();) {
                PrimitiveNode pn = pIt.next();
                NodeLayer[] nl = pn.getNodeLayers();
                for (int i = 0; i < nl.length; i++) {
                    Layer lay = nl[i].getLayer();
                    if (lay.getFunction().isSubstrate()) {
                        possiblePrimitives.add(pn);
                        break;
                    }
                }
            }
            for (Iterator<ArcProto> pIt = tech.getArcs(); pIt.hasNext();) {
                ArcProto ap = pIt.next();
                for (int i = 0; i < ap.getNumArcLayers(); i++) {
                    Layer lay = ap.getLayer(i);
                    if (lay.getFunction().isSubstrate()) {
                        if (lay.getFunction().isWell())
                            pWellLayer = lay;
                        else
                            nWellLayer = lay;
                        possiblePrimitives.add(ap);
                        break;
                    }
                }
            }
        }
        int errorCount = doNewWay();
        showStatistics();

        // report the number of errors found
        timer.end();
        if (errorCount == 0) {
            System.out.println("No Well errors found (took " + timer + ")");
        } else {
//        	if (Job.getDebug())
//        		this.errorLogger.exportErrorLogger(cell.getName()+"logger.xml");
            System.out.println("FOUND " + errorCount + " WELL ERRORS (took " + timer + ")");

        }
        return errorCount;
    }

    private WellCon getNextWellCon(int threadIndex) {
        synchronized (wellConLists[threadIndex]) {
            while (wellConIterator[threadIndex].hasNext()) {
                WellCon wc = wellConIterator[threadIndex].next();
                if (wc.getWellNum() == null)
                    return wc;
            }
        }

        // not found in this list: try the others
        int numLists = wellConIterator.length;
        for (int i = 1; i < numLists; i++) {
            int otherList = (threadIndex + i) % numLists;
            synchronized (wellConLists[otherList]) {
                while (wellConIterator[otherList].hasNext()) {
                    WellCon wc = wellConIterator[otherList].next();
                    if (wc.getWellNum() == null)
                        return wc;
                }
            }
        }
        return null;
    }

    private void checkSpreadResults() {
        int cons = 0;
        int badCons = 0;
        for (WellCon wc: wellCons) {
            assert wc.getWellNum() != null;
            RTNode<WellBound> topSearch = Utils.canBeSubstrateTap(wc.getFun()) ? pWellRoot: nWellRoot;
            Rectangle2D searchArea = new Rectangle2D.Double(wc.getBound().getCenterX(), wc.getBound().getCenterY(), 0, 0);
            for (Iterator<WellBound> it = new RTNode.Search<WellBound>(searchArea, topSearch, true); it.hasNext(); ) {
                WellBound wb = it.next();
                cons++;
                if (wc.getWellNum().getIndex() != wb.netID.getIndex()) {
                    badCons++;
                }
            }
        }
        System.out.println(cons + " cons " + badCons + " badCons");
        checkSpreadResults(nWellRoot);
        checkSpreadResults(pWellRoot);
    }

    private void checkSpreadResults(RTNode<WellBound> topSearch) {
        int pairs = 0;
        int badPairs = 0;
        for (Iterator<WellBound> allIt = new RTNode.Search<WellBound>(topSearch); allIt.hasNext(); ) {
            WellBound wb = allIt.next();
            if (wb.netID == null) {
                continue;
            }
            for (Iterator<WellBound> it = new RTNode.Search<WellBound>(wb.bound, topSearch, true); it.hasNext(); ) {
                WellBound wb2 = it.next();
                pairs++;
                if (wb.netID.getIndex() != wb2.netID.getIndex()) {
                    badPairs++;
                }
            }
        }
        System.out.println(pairs + " pairs " + badPairs + " badPairs");
    }

    private void spreadSeeds(int threadIndex) {
        assert !SIMPLE_SPREAD;
        for (;;) {
            WellCon wc = getNextWellCon(threadIndex);

            if (wc == null)
                break;

            // see if this contact is a marked well
            Rectangle2D searchArea = new Rectangle2D.Double(wc.getBound().getCenterX(), wc.getBound().getCenterY(), 0, 0);
            RTNode<WellBound> topSearch = nWellRoot;
            if (Utils.canBeSubstrateTap(wc.getFun()))
                topSearch = pWellRoot;
            boolean geomFound = false;
            for (Iterator<WellBound> sea = new RTNode.Search<WellBound>(searchArea, topSearch, true); sea.hasNext();) {
                WellBound wb = sea.next();
                geomFound = true;
                wc.setWellNum(wb.netID);
                if (wc.getWellNum() != null)
                    break;
            }

            if (wc.getWellNum() != null)
                continue;

            // mark it and spread the value
            wc.setWellNum(new NetValues());

            // if nothing to spread, give an error
            if (!geomFound) {
                String errorMsg = "N-Well contact is floating";
                if (Utils.canBeSubstrateTap(wc.getFun()))
                    errorMsg = "P-Well contact is floating";
                errorLogger.logError(errorMsg, EPoint.fromLambda(wc.getBound().getCenterX(), wc.getBound().getCenterY()), cell, 0);
                continue;
            }

            // spread out through the well area
            Utils.spreadWellSeed(wc.getBound().getCenterX(), wc.getBound().getCenterY(), wc.getWellNum(), topSearch,
                    threadIndex);
        }
    }

    public class SpreadInThread extends PTask {
        private int threadIndex;

        public SpreadInThread(PJob job, int index) {
            super(job);
            threadIndex = index;
        }

        public void execute() {
            spreadSeeds(threadIndex);
        }
    }

    private int doNewWay() {

        int numberOfThreads = 1;
        if (wellPrefs.parallelWellAnalysis)
            numberOfThreads = Runtime.getRuntime().availableProcessors();
        if (numberOfThreads > 1) {
            int maxProc = wellPrefs.maxProc;
            if (maxProc > 0)
                numberOfThreads = maxProc;
        }

        hasNCon = false;
        hasPCon = false;
        NetValues.numberOfMerges = 0;

        pWellRoot = RTNode.makeTopLevel();
        nWellRoot = RTNode.makeTopLevel();

        // enumerate the hierarchy below here
        ElapseTimer timer = ElapseTimer.createInstance().start();
        WellCheckVisitor wcVisitor = new WellCheckVisitor();
        HierarchyEnumerator.enumerateCell(cell, VarContext.globalContext, wcVisitor);
        int numPRects = getTreeSize(pWellRoot);
        int numNRects = getTreeSize(nWellRoot);
        timer.end();
        System.out.println("   Geometry collection found " + (numPRects + numNRects) + " well pieces, took "
                + timer);

        wcVisitor.clear();
        wcVisitor = null;

        if(numberOfThreads <= 0)
        	numberOfThreads = Runtime.getRuntime().availableProcessors();
        assert numberOfThreads > 0;

        BitSet connectedNetValues = null;
        if (SIMPLE_SPREAD)
        {
            timer.start();
            // analyze the contacts
            NetValues.reset();
            int numTasks = numberOfThreads;
            List<PartialSpread> partialSpreads = new ArrayList<PartialSpread>();
            for (int taskIndex = 0; taskIndex < numTasks; taskIndex++) {
                partialSpreads.add(new PartialSpread(numTasks, taskIndex));
            }
            PartialSpreadResult[] results = new PartialSpreadResult[numTasks];
            try {
                ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
                List<Future<PartialSpreadResult>> futureResults = executorService.invokeAll(partialSpreads);
                assert futureResults.size() == numTasks;
                executorService.shutdown();
                boolean ok = executorService.awaitTermination(100, TimeUnit.DAYS);
                assert ok;
                for (int taskIndex = 0; taskIndex < numTasks; taskIndex++) {
                    Future<PartialSpreadResult> futureResult = futureResults.get(taskIndex);
                    assert futureResult.isDone();
                    results[taskIndex] = futureResult.get();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            timer.end();
            String msg = "   Geometry analysis ";
            if (numberOfThreads > 1)
                msg += "used " + numberOfThreads + " threads and ";
            msg += "took ";
            System.out.println(msg + timer);

            timer.start();

            int[] pMap = initMap(pWellCount);
            for (PartialSpreadResult r: results) {
                for (Point pair: r.pWellPairs) connectMap(pMap, pair.x, pair.y);
                r.pWellPairs.clear();
            }
            closureMap(pMap);
            Map<Integer,NetValues> pNetValues = new HashMap<Integer,NetValues>();
            for (Iterator<WellBound> it = new RTNode.Search<WellBound>(pWellRoot); it.hasNext(); ) {
                WellBound wb = it.next();
                int ind = pMap[wb.getID()];
                NetValues nv = pNetValues.get(ind);
                if (nv == null) {
                    nv = new NetValues();
                    pNetValues.put(ind, nv);
                }
                wb.setNetID(nv);
            }
            pMap = null;
            pNetValues = null;

            int[] nMap = initMap(nWellCount);
            for (PartialSpreadResult r: results) {
                for (Point pair: r.nWellPairs) connectMap(nMap, pair.x, pair.y);
                r.nWellPairs.clear();
            }
            closureMap(nMap);
            Map<Integer,NetValues> nNetValues = new HashMap<Integer,NetValues>();
            for (Iterator<WellBound> it = new RTNode.Search<WellBound>(nWellRoot); it.hasNext(); ) {
                WellBound wb = it.next();
                int ind = nMap[wb.getID()];
                NetValues nv = nNetValues.get(ind);
                if (nv == null) {
                    nv = new NetValues();
                    nNetValues.put(ind, nv);
                }
                wb.setNetID(nv);
            }
            nMap = null;
            nNetValues = null;

            connectedNetValues = new BitSet();
            for (PartialSpreadResult r: results) {
                for (Map.Entry<WellCon,WellBound> e: r.conBound.entrySet()) {
                    WellCon wc = e.getKey();
                    assert wc.getWellNum() == null;
                    WellBound wb = e.getValue();
                    NetValues nv = wb.getNetID();
                    assert nv != null;
                    connectedNetValues.set(nv.getIndex());
                    wc.setWellNum(nv);
                }
                r.conBound.clear();
            }
            results = null;

            timer.end();
            System.out.println("NetValues propagation took " + timer);

            assert NetValues.numberOfMerges == 0;
        } else
        {
            IThreadPool.NUM_THREADS = Integer.valueOf(numberOfThreads);
            try {
                threadPool = SCALA_FORKJOIN ? ThreadPoolJdkForkJoin.initialize(numberOfThreads) : ThreadPool.initialize();
            } catch (PoolExistsException e) {
            } catch (UnknownSchedulerException e) {
            }

            timer.start();

            try {

                // make arrays of well contacts clustered for each processor
                assignWellContacts(numberOfThreads);

                if (Job.getDebug()) {
                    timer.end();

                    System.out.println("   Assign well contacts took: " + timer);

                    timer.start();
                }

                // analyze the contacts
                NetValues.reset();
                if (numberOfThreads <= 1)
                    spreadSeeds(0);
                else {
                    PJob spreadJob = new PJob(threadPool);
                    for (int i = 0; i < numberOfThreads; i++)
                        spreadJob.add(new SpreadInThread(spreadJob, i));
                    spreadJob.execute();
                }

                timer.end();
                String msg = "   Geometry analysis ";
                if (numberOfThreads > 1)
                    msg += "used " + numberOfThreads + " threads and ";
                msg += "took ";
                System.out.println(msg + timer);

                try {
                    threadPool.shutdown();
                    threadPool.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (Job.getDebug()) {
                    System.out.println("   Amount of merges: " + NetValues.numberOfMerges);
                    checkSpreadResults();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        if (Job.getDebug()) {
            checkSpreadResults();
        }

        timer.start();
        StrategyParameter parameter = new StrategyParameter(wellCons, wellPrefs, cell, errorLogger);

        // create analysis steps
        List<WellCheckAnalysisStrategy> analysisParts = CollectionFactory.createArrayList();
        analysisParts.add(new ShortCircuitCheck(parameter));
        analysisParts.add(new OnRailCheck(parameter, networkExportAvailable, transistors));
        analysisParts.add(new ConnectionCheck(parameter, hasPCon, hasNCon, pWellRoot, nWellRoot, connectedNetValues));
        analysisParts.add(new DRCCheck(parameter, pWellLayer, nWellLayer, pWellRoot, nWellRoot));
        analysisParts.add(new DistanceCheck(parameter, worstPWellDist, worstPWellCon, worstPWellEdge,
                worstNWellDist, worstNWellCon, worstNWellEdge, pWellRoot, nWellRoot));

        // execute analysis steps
        for (WellCheckAnalysisStrategy strategy : analysisParts) {
            strategy.execute();
        }

        timer.end();
        System.out.println("   Additional analysis took " + timer);

		if (wellPrefs.disablePopups) errorLogger.disablePopups();
        errorLogger.termLogging(true);
        int errorCount = errorLogger.getNumErrors();

        return errorCount;
    }

    private static class PartialSpreadResult {
        private final Map<WellCon,WellBound> conBound = new HashMap<WellCon, WellBound>();
        private final List<Point> pWellPairs = new ArrayList<Point>();
        private final List<Point> nWellPairs = new ArrayList<Point>();
    }
    
    private class PartialSpread implements Callable<PartialSpreadResult> {
        private final int numTasks;
        private final int taskIndex;
        
        private PartialSpread(int numTasks, int taskIndex) {
            this.numTasks = numTasks;
            this.taskIndex = taskIndex;
        }

        @Override
        public PartialSpreadResult call() {
            PartialSpreadResult result = new PartialSpreadResult();
            int ind = 0;
            wellLoop:
            for (WellCon wc: wellCons) {
                if (ind++ % numTasks != taskIndex) continue;
                assert wc.getWellNum() == null;
                RTNode<WellBound> topSearch = Utils.canBeSubstrateTap(wc.getFun()) ? pWellRoot: nWellRoot;
                Rectangle2D searchArea = new Rectangle2D.Double(wc.getBound().getCenterX(), wc.getBound().getCenterY(), 0, 0);
                for (Iterator<WellBound> it = new RTNode.Search<WellBound>(searchArea, topSearch, true); it.hasNext(); ) {
                    WellBound wb = it.next();
                    result.conBound.put(wc, wb);
                    continue wellLoop;
                }
                String errorMsg = "N-Well contact is floating";
                if (Utils.canBeSubstrateTap(wc.getFun()))
                    errorMsg = "P-Well contact is floating";
                errorLogger.logError(errorMsg, EPoint.fromLambda(wc.getBound().getCenterX(), wc.getBound().getCenterY()), cell, 0);
                wc.setWellNum(new NetValues());
            }
            assert ind == wellCons.size();
            ind = wellBoundPairs(0, pWellRoot, pWellRoot, result.pWellPairs);
            assert ind == pWellCount;
            ind = wellBoundPairs(0, nWellRoot, nWellRoot, result.nWellPairs);
            assert ind == nWellCount;
            return result;
        }

        private int wellBoundPairs(int ind, RTNode<WellBound> topSearch, RTNode<WellBound> current, List<Point> pairs) {
            for (int j = 0; j < current.getTotal(); j++) {
                if (current.getFlag()) {
                    WellBound wb = current.getChildLeaf(j);
                    if (ind++ % numTasks != taskIndex) continue;
                    for (Iterator<WellBound> it = new RTNode.Search<WellBound>(wb.bound, topSearch, true); it.hasNext(); ) {
                        WellBound wb2 = it.next();
                        if (wb.getID() < wb2.getID()) {
                            pairs.add(new Point(wb.getID(), wb2.getID()));
                        }
                    }
                } else {
                    RTNode<WellBound> child = current.getChildTree(j);
                    ind = wellBoundPairs(ind, topSearch, child, pairs);
                }
            }
            return ind;
        }
    }
    
    /**
     * Initialize equivalence map.
     * @param size number of elements in equivalence map
     * @return integer array representing equivalence map consisting of disjoint elements.
     */
    static int[] initMap(int size) {
        int[] map = new int[size];
        for (int i = 0; i < map.length; i++) {
            map[i] = i;
        }
        return map;
    }

    /**
     * Merge classes of equivalence map to which elements a1 and a2 belong.
     * Returns true if the classes were different
     */
    static boolean connectMap(int[] map, int a1, int a2) {
        int m1, m2, m;

        for (m1 = a1; map[m1] != m1; m1 = map[m1]);
        for (m2 = a2; map[m2] != m2; m2 = map[m2]);
        boolean changed = m1 != m2;
        m = m1 < m2 ? m1 : m2;

        for (;;) {
            int k = map[a1];
            map[a1] = m;
            if (a1 == k) {
                break;
            }
            a1 = k;
        }
        for (;;) {
            int k = map[a2];
            map[a2] = m;
            if (a2 == k) {
                break;
            }
            a2 = k;
        }
        return changed;
    }

    /**
     * Obtain canonical representation of equivalence map.
     */
    static void closureMap(int[] map) {
        for (int i = 0; i < map.length; i++) {
            map[i] = map[map[i]];
        }
    }

    public static class StrategyParameter {
        private final List<WellCon> wellCons;
        private final WellCheckPreferences wellPrefs;
        private final Cell cell;
        private final ErrorLogger errorLogger;

        /**
         * @param wellCons
         * @param wellPrefs
         * @param cell
         */
        public StrategyParameter(List<WellCon> wellCons, WellCheckPreferences wellPrefs, Cell cell,
                ErrorLogger errorLogger) {
            super();
            this.wellCons = wellCons;
            this.wellPrefs = wellPrefs;
            this.cell = cell;
            this.errorLogger = errorLogger;
        }

        public List<WellCon> getWellCons() {
            return wellCons;
        }

        public WellCheckPreferences getWellPrefs() {
            return wellPrefs;
        }

        public Cell getCell() {
            return cell;
        }

        // Apply translation to place the bounding box of the well contact
        // in the correct location on the top cell.
        private Rectangle2D placedWellCon(WellCon wc) {
            Rectangle2D orig = wc.getNi().getBounds();
            return new Rectangle2D.Double(wc.getBound().getCenterX() - orig.getWidth() / 2, wc.getBound().getCenterY()
                    - orig.getHeight() / 2, orig.getWidth(), orig.getHeight());
        }

        public void logError(String message) {
            errorLogger.logError(message, cell, 0);
        }

        public void logError(String message, Object... wblist) {
            List<Object> list = new ArrayList<Object>();
            for (Object w : wblist) {
                if (w instanceof WellBound) {
                    list.add(((WellBound) w).getBounds());
                } else if (w instanceof WellCon) {
                    // calculate the correct location of WellCon bound with respect to the top cell
                	WellCon wc = (WellCon)w;
                	list.add(wc.getBound());
                }
            }
            errorLogger.logMessage(message, list, cell, 0, true);
        }
    }

    @SuppressWarnings("unchecked")
    private void assignWellContacts(int numberOfThreads) {
        wellConIterator = new Iterator[numberOfThreads];
        wellConLists = new List[numberOfThreads];

        // load the lists
        if (numberOfThreads == 1) {
            wellConLists[0] = wellCons;
        } else {
            for (int i = 0; i < numberOfThreads; i++) {
                wellConLists[i] = new ArrayList<WellCon>();
            }
            if (Utils.WORKDISTRIBUTION == WorkDistributionStrategy.cluster) {
                // the new way: cluster the well contacts together
                Rectangle2D cellBounds = cell.getBounds();
                Point2D ctr = new Point2D.Double(cellBounds.getCenterX(), cellBounds.getCenterY());
                Point2D[] farPoints = new Point2D[numberOfThreads];

                for (int i = 0; i < numberOfThreads; i++) {
                    double farthest = 0;
                    farPoints[i] = new Point2D.Double(0, 0);
                    for (WellCon wc : wellCons) {
                        double dist = 0;
                        if (i == 0)
                            dist += wc.getCenter().distance(ctr);
                        else {
                            for (int j = 0; j < i; j++)
                                dist += wc.getCenter().distance(farPoints[j]);
                        }
                        if (dist > farthest) {
                            farthest = dist;
                            farPoints[i].setLocation(wc.getCenter());
                        }
                    }
                }

                // find the center of the cell
                for (WellCon wc : wellCons) {
                    double minDist = Double.MAX_VALUE;
                    int threadNum = 0;
                    for (int j = 0; j < numberOfThreads; j++) {
                        double dist = wc.getCenter().distance(farPoints[j]);
                        if (dist < minDist) {
                            minDist = dist;
                            threadNum = j;
                        }
                    }
                    wellConLists[threadNum].add(wc);
                }
            } else if (Utils.WORKDISTRIBUTION == WorkDistributionStrategy.random) {

                for (int i = 0; i < wellCons.size(); i++)
                    wellConLists[i % numberOfThreads].add(wellCons.get(i));

            } else if (Utils.WORKDISTRIBUTION == WorkDistributionStrategy.bucket) {

                GridDim dim = calculateGridDim(numberOfThreads);

                double sizeCellX = cell.getDefWidth() / dim.xDim;
                double sizeCellY = cell.getDefHeight() / dim.yDim;
                int stepWidth = (wellCons.size() < numberOfThreads) ? wellCons.size() : wellCons.size() / numberOfThreads;

                Parallel.For(new BlockedRange1D(0, wellCons.size(), stepWidth),
                        new WorkDistributionTask(sizeCellX, sizeCellY, dim), threadPool);
            }
        }

        // create iterators over the lists
        for (int i = 0; i < numberOfThreads; i++)
            wellConIterator[i] = wellConLists[i].iterator();
    }

    public class WorkDistributionTask extends PForTask<BlockedRange1D> {

        private double sizeX;
        private double sizeY;
        private GridDim dim;

        public WorkDistributionTask(double sizeX, double sizeY, GridDim dim) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.dim = dim;
        }

        @Override
        public void execute() {
            for (int i = range.start(); i < range.end(); i++) {
                WellCon con = wellCons.get(i);
                GridDim tmpDim = calculateBucket(con, sizeX, sizeY);
                int threadId = tmpDim.xDim + tmpDim.yDim * dim.xDim;
                CollectionFactory.threadSafeListAdd(con, wellConLists[threadId]);
            }
        }

    }

    private GridDim calculateBucket(WellCon con, double sizeX, double sizeY) {
        GridDim result = new GridDim();

        result.xDim = (int) ((con.getBound().getCenterX() - cell.getBounds().getMinX()) / sizeX);
        result.yDim = (int) ((con.getBound().getCenterY() - cell.getBounds().getMinY()) / sizeY);

        return result;
    }

    private GridDim calculateGridDim(int numberOfThreads) {
        GridDim result = new GridDim();

        for (int i = (int) Math.sqrt(numberOfThreads); i >= 1; i--) {
            if ((i * (numberOfThreads / i)) == numberOfThreads) {
                result.xDim = i;
                result.yDim = numberOfThreads / i;
                return result;
            }
        }

        return null;
    }

    private static class GridDim {
        private int xDim;
        private int yDim;
    }

    // provide this information in the R-Tree structure
    private int getTreeSize(RTNode<WellBound> rtree) {
        int total = 0;
        if (rtree.getFlag())
            total += rtree.getTotal();
        else {
            for (int j = 0; j < rtree.getTotal(); j++) {
                RTNode<WellBound> child = rtree.getChildTree(j);
                total += getTreeSize(child);
            }
        }
        return total;
    }

    public static class Transistor {
        private AtomicInteger drainNet = new AtomicInteger();
        private AtomicInteger sourceNet = new AtomicInteger();

        public AtomicInteger getDrainNet() {
            return drainNet;
        }

        public void setDrainNet(AtomicInteger drainNet) {
            this.drainNet = drainNet;
        }

        public AtomicInteger getSourceNet() {
            return sourceNet;
        }

        public void setSourceNet(AtomicInteger sourceNet) {
            this.sourceNet = sourceNet;
        }
    }

    /**
     * Class to define an R-Tree leaf node for geometry in the blockage data
     * structure.
     */
    public static class WellBound implements RTBounds {
        private final int id;
        private final FixpRectangle bound;
        private NetValues netID;

        WellBound(int id, FixpRectangle bound) {
            this.id = id;
            this.bound = bound;
            this.netID = null;
        }

        public int getID() {
            return id;
        }

        @Override
        public FixpRectangle getBounds() {
            return bound;
        }

        public NetValues getNetID() {
            return netID;
        }

        public void setNetID(NetValues netNum) {
            this.netID = netNum;
        }

        @Override
        public String toString() {
            return "Well Bound on net " + netID.getIndex();
        }
    }

    private static class NetRails {
        boolean onGround;
        boolean onPower;
        boolean onExport;
    }

    public static class WellNet {
        private List<Point2D> pointsOnNet;
        private List<WellCon> contactsOnNet;
        private PrimitiveNode.Function fun;

        /**
         * @param pointsOnNet
         * @param contactsOnNet
         * @param fun
         */
        public WellNet(List<Point2D> pointsOnNet, List<WellCon> contactsOnNet, Function fun) {
            super();
            this.pointsOnNet = pointsOnNet;
            this.contactsOnNet = contactsOnNet;
            this.fun = fun;
        }

        public List<Point2D> getPointsOnNet() {
            return pointsOnNet;
        }

        public void setPointsOnNet(List<Point2D> pointsOnNet) {
            this.pointsOnNet = pointsOnNet;
        }

        public List<WellCon> getContactsOnNet() {
            return contactsOnNet;
        }

        public void setContactsOnNet(List<WellCon> contactsOnNet) {
            this.contactsOnNet = contactsOnNet;
        }

        public PrimitiveNode.Function getFun() {
            return fun;
        }

        public void setFun(PrimitiveNode.Function fun) {
            this.fun = fun;
        }
    }

    /**
     * Comparator class for sorting Rectangle2D objects by their size.
     */
    private static class RectangleBySize implements Comparator<Rectangle2D> {
        /**
         * Method to sort Rectangle2D objects by their size.
         */
        public int compare(Rectangle2D b1, Rectangle2D b2) {
            double s1 = b1.getWidth() * b1.getHeight();
            double s2 = b2.getWidth() * b2.getHeight();
            if (s1 > s2)
                return -1;
            if (s1 < s2)
                return 1;
            return 0;
        }
    }

    private class WellCheckVisitor extends HierarchyEnumerator.Visitor {
        private Map<Cell, List<Rectangle2D>> essentialPWell;
        private Map<Cell, List<Rectangle2D>> essentialNWell;
        private Map<Network, NetRails> networkCache;
        private Map<Integer, Transistor> neighborCache;

        public WellCheckVisitor() {
            essentialPWell = new HashMap<Cell, List<Rectangle2D>>();
            essentialNWell = new HashMap<Cell, List<Rectangle2D>>();
            networkCache = new HashMap<Network, NetRails>();
            networkExportAvailable = new HashSet<Integer>();
            neighborCache = new HashMap<Integer, Transistor>();
        }

        public boolean enterCell(HierarchyEnumerator.CellInfo info) {
            // Checking if job is scheduled for abort or already aborted
            if (job != null && job.checkAbort())
                return false;

            // make sure essential well areas are gathered
            Cell cell = info.getCell();
            ensureCellCached(cell);
            return true;
        }

        @Override
        public void exitCell(HierarchyEnumerator.CellInfo info) {
            // Checking if job is scheduled for abort or already aborted
            if (job != null && job.checkAbort())
                return;

            // get the object for merging all of the wells in this cell
            Cell cell = info.getCell();
            List<Rectangle2D> pWellsInCell = essentialPWell.get(cell);
            List<Rectangle2D> nWellsInCell = essentialNWell.get(cell);
            for (Rectangle2D b : pWellsInCell) {
                FixpRectangle bounds = FixpRectangle.from(b);
                DBMath.transformRect(bounds, info.getTransformToRoot());
                pWellRoot = RTNode.linkGeom(null, pWellRoot, new WellBound(pWellCount++, bounds));
            }
            for (Rectangle2D b : nWellsInCell) {
                FixpRectangle bounds = FixpRectangle.from(b);
                DBMath.transformRect(bounds, info.getTransformToRoot());
                nWellRoot = RTNode.linkGeom(null, nWellRoot, new WellBound(nWellCount++, bounds));
            }
        }

        private void addNetwork(Network net, AtomicInteger netNum, HierarchyEnumerator.CellInfo cinfo,
                Transistor trans) {
            if (net != null) {
            	int num = cinfo.getNetID(net);
                Integer iNum = Integer.valueOf(num);
                netNum.set(num);

                if (!transistors.containsKey(iNum)) {
                    List<Transistor> tmpList = new LinkedList<Transistor>();
                    transistors.put(iNum, tmpList);
                }
                transistors.get(iNum).add(trans);
            }
        }

        public boolean visitNodeInst(Nodable no, HierarchyEnumerator.CellInfo info) {
            NodeInst ni = no.getNodeInst();

            // look for well and substrate contacts
            PrimitiveNode.Function fun = ni.getFunction();
            Netlist netList = info.getNetlist();

            if ((fun.isNTypeTransistor() || fun.isPTypeTransistor())) {
                Transistor trans = new Transistor();
                addNetwork(netList.getNetwork(ni.getTransistorDrainPort()), trans.drainNet, info, trans);
                addNetwork(netList.getNetwork(ni.getTransistorSourcePort()), trans.sourceNet, info, trans);
            } else if (Utils.canBeSubstrateTap(fun) || Utils.canBeWellTap(fun)) {
                // Allowing more than one port for well resistors.
                for (Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext();) {
                    // PortInst pi = ni.getOnlyPortInst();
                    PortInst pi = pIt.next();

                    Network net = netList.getNetwork(pi);
                    int tmpNetNum = 0;
                    if (net == null)
                        tmpNetNum = -1;
                    else
                        tmpNetNum = info.getNetID(net);

                    ERectangle er = ni.getBounds();
                    Rectangle2D bounds = new Rectangle2D.Double(er.getMinX(), er.getMinY(), er.getWidth(), er.getHeight());
                    info.getTransformToRoot().transform(bounds, bounds);
                    WellCon wc = new WellCon(bounds, tmpNetNum, null, false, false, fun, ni);

                    if (net != null) {
                        // PWell: must be on ground or NWell: must be on power
                        Network parentNet = net;
                        HierarchyEnumerator.CellInfo cinfo = info;
                        while (cinfo.getParentInst() != null) {
                            parentNet = cinfo.getNetworkInParent(parentNet);
                            cinfo = cinfo.getParentInfo();
                        }
                        if (parentNet != null) {
                            NetRails nr = networkCache.get(parentNet);
                            if (nr == null) {
                                nr = new NetRails();
                                networkCache.put(parentNet, nr);
                                Iterator<Export> it = parentNet.getExports();
                                while (it.hasNext()) {
                                    Export exp = it.next();

                                    networkExportAvailable.add(new Integer(parentNet.getNetIndex()));

                                    if (exp.isGround())
                                        nr.onGround = true;
                                    if (exp.isPower())
                                        nr.onPower = true;
                                    nr.onExport = true;
                                }
                            }
                            boolean searchWell = (Utils.canBeSubstrateTap(fun));

                            if (Utils.canBeSubstrateTap(wc.getFun()))
                                hasPCon = true;
                            else
                                hasNCon = true;

                            if (searchWell)
                                hasPCon = true;
                            else
                                hasNCon = true;

                            if ((searchWell && nr.onGround) || (!searchWell && nr.onPower))
                                wc.setOnProperRail(true);
                            if (nr.onExport)
                                wc.setOnRail(true);
                        }
                    }
                    wellCons.add(wc);
                }
            }

            return true;
        }

        private void ensureCellCached(Cell cell) {
            List<Rectangle2D> pWellsInCell = essentialPWell.get(cell);
            List<Rectangle2D> nWellsInCell = essentialNWell.get(cell);
            if (pWellsInCell == null) {
                // gather all wells in the cell
                pWellsInCell = new ArrayList<Rectangle2D>();
                nWellsInCell = new ArrayList<Rectangle2D>();
                for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
                    NodeInst ni = it.next();
                    if (ni.isCellInstance())
                        continue;
                    PrimitiveNode pn = (PrimitiveNode) ni.getProto();
                    if (!possiblePrimitives.contains(pn))
                        continue;

                    // Getting only ercLayers
                    Poly[] nodeInstPolyList = pn.getTechnology().getShapeOfNode(ni, true, true, ercLayers);
                    int tot = nodeInstPolyList.length;
                    for (int i = 0; i < tot; i++) {
                        Poly poly = nodeInstPolyList[i];
                        Layer layer = poly.getLayer();
                        FixpTransform trans = ni.rotateOut();
                        poly.transform(trans);
                        Rectangle2D bound = poly.getBounds2D();
                        WellType wellType = getWellLayerType(layer);
                        if (wellType == WellType.pwell)
                            pWellsInCell.add(bound);
                        else if (wellType == WellType.nwell)
                            nWellsInCell.add(bound);
                    }
                }
                for (Iterator<ArcInst> it = cell.getArcs(); it.hasNext();) {
                    ArcInst ai = it.next();
                    ArcProto ap = ai.getProto();
                    if (!possiblePrimitives.contains(ap))
                        continue;

                    // Getting only ercLayers
                    Poly[] arcInstPolyList = ap.getTechnology().getShapeOfArc(ai, ercLayers);
                    int tot = arcInstPolyList.length;
                    for (int i = 0; i < tot; i++) {
                        Poly poly = arcInstPolyList[i];
                        Layer layer = poly.getLayer();
                        Rectangle2D bound = poly.getBounds2D();
                        WellType wellType = getWellLayerType(layer);
                        if (wellType == WellType.pwell)
                            pWellsInCell.add(bound);
                        else if (wellType == WellType.nwell)
                            nWellsInCell.add(bound);
                    }
                }

                // eliminate duplicates
                eliminateDuplicates(pWellsInCell);
                eliminateDuplicates(nWellsInCell);

                // save results
                essentialPWell.put(cell, pWellsInCell);
                essentialNWell.put(cell, nWellsInCell);
            }
        }

        private void eliminateDuplicates(List<Rectangle2D> wbList) {
            // first sort by size
            Collections.sort(wbList, new RectangleBySize());
            for (int i = 0; i < wbList.size(); i++) {
                Rectangle2D b = wbList.get(i);
                for (int j = 0; j < i; j++) {
                    Rectangle2D prevB = wbList.get(j);
                    if (prevB.contains(b)) {
                        wbList.remove(i);
                        i--;
                        break;
                    }
                }
            }
        }

        public void clear() {
            neighborCache.clear();
            networkCache.clear();

            neighborCache = null;
            networkCache = null;
        }
    }

    private static final Layer.Function[] ercLayersArray = { Layer.Function.WELLP, Layer.Function.WELL,
            Layer.Function.WELLN, Layer.Function.SUBSTRATE, Layer.Function.IMPLANTP, Layer.Function.IMPLANT,
            Layer.Function.IMPLANTN };
    private static final Layer.Function.Set ercLayers = new Layer.Function.Set(ercLayersArray);

    /**
     * Method to return nonzero if layer "layer" is a well/select layer.
     *
     * @return 1: P-Well, 2: N-Well
     */
    private static WellType getWellLayerType(Layer layer) {
        Layer.Function fun = layer.getFunction();
        if (fun == Layer.Function.WELLP)
            return WellType.pwell;
        if (fun == Layer.Function.WELL || fun == Layer.Function.WELLN)
            return WellType.nwell;
        return WellType.none;
    }

    // **************************************** STATISTICS ****************************************

    private void initStatistics() {
        if (Utils.GATHERSTATISTICS) {
            Utils.numObjSearches = 0;
            Utils.wellBoundSearchOrder = Collections.synchronizedList(new ArrayList<WellBoundRecord>());
        }
    }

    private void showStatistics() {
        if (Utils.GATHERSTATISTICS) {
            System.out.println("SEARCHED " + Utils.numObjSearches + " OBJECTS");
            new ShowWellBoundOrder();
        }
    }

    public static class WellBoundRecord {
        private WellBound wb;
        private int processor;

        public WellBoundRecord(WellBound w, int p) {
            wb = w;
            processor = p;
        }

        public WellBound getWb() {
            return wb;
        }

        public void setWb(WellBound wb) {
            this.wb = wb;
        }

        public int getProcessor() {
            return processor;
        }

        public void setProcessor(int processor) {
            this.processor = processor;
        }
    }

    private class ShowWellBoundOrder extends EModelessDialog {
        private Timer vcrTimer;
        private long vcrLastAdvance;
        private int wbIndex;
        private int speed;
        private JTextField tf;
        private Highlighter h;
        private Color[] hColors = new Color[] { Color.WHITE, Color.RED, Color.GREEN, Color.BLUE };

        public ShowWellBoundOrder() {
            super(TopLevel.isMDIMode() ? TopLevel.getCurrentJFrame() : null);
            initComponents();
            finishInitialization();
            setVisible(true);
            wbIndex = 0;
            EditWindow wnd = EditWindow.getCurrent();
            h = wnd.getHighlighter();
            h.clear();
        }

        private void initComponents() {
            getContentPane().setLayout(new GridBagLayout());
            setTitle("Show ERC Progress");
            setName("");
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent evt) {
                    closeDialog();
                }
            });
            GridBagConstraints gridBagConstraints;

            JButton go = new JButton("Go");
            go.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    goNow();
                }
            });
            gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.insets = new Insets(4, 4, 4, 4);
            getContentPane().add(go, gridBagConstraints);

            JButton stop = new JButton("Stop");
            stop.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    stopNow();
                }
            });
            gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.insets = new Insets(4, 4, 4, 4);
            getContentPane().add(stop, gridBagConstraints);

            JLabel lab = new JLabel("Speed:");
            gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 1;
            gridBagConstraints.insets = new Insets(4, 4, 4, 4);
            getContentPane().add(lab, gridBagConstraints);

            speed = 1;
            tf = new JTextField(Integer.toString(speed));
            tf.getDocument().addDocumentListener(new BoundsPlayerDocumentListener());
            gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 1;
            gridBagConstraints.gridy = 1;
            gridBagConstraints.insets = new Insets(4, 4, 4, 4);
            getContentPane().add(tf, gridBagConstraints);

            pack();
        }

        private void updateSpeed() {
            speed = TextUtils.atoi(tf.getText());
            if (vcrTimer != null)
                vcrTimer.setDelay(speed);
        }

        private void goNow() {
            if (vcrTimer == null) {
                ActionListener taskPerformer = new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        tick();
                    }
                };
                vcrTimer = new Timer(speed, taskPerformer);
                vcrLastAdvance = System.currentTimeMillis();
                vcrTimer.start();
            }
        }

        private void stopNow() {
            if (vcrTimer == null)
                return;
            vcrTimer.stop();
            vcrTimer = null;
        }

        private void tick() {
            // see if it is time to advance the VCR
            long curtime = System.currentTimeMillis();
            if (curtime - vcrLastAdvance < speed)
                return;
            vcrLastAdvance = curtime;

            if (wbIndex >= Utils.wellBoundSearchOrder.size())
                stopNow();
            else {
                WellBoundRecord wbr = Utils.wellBoundSearchOrder.get(wbIndex++);
                h.addPoly(new Poly(wbr.wb.bound), cell, hColors[wbr.processor]);
                h.finished();
            }
        }

        private class BoundsPlayerDocumentListener implements DocumentListener {
            BoundsPlayerDocumentListener() {
            }

            public void changedUpdate(DocumentEvent e) {
                updateSpeed();
            }

            public void insertUpdate(DocumentEvent e) {
                updateSpeed();
            }

            public void removeUpdate(DocumentEvent e) {
                updateSpeed();
            }
        }
    }
}
