/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: SeaOfGatesHandlers.java
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
package com.sun.electric.tool.routing.seaOfGates;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.Snapshot;
import com.sun.electric.database.constraint.Layout;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.id.CellId;
import com.sun.electric.database.id.ExportId;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.text.CellName;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.technology.EdgeH;
import com.sun.electric.technology.EdgeV;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.Technology.ArcLayer;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.routing.Routing;
import com.sun.electric.tool.routing.Routing.SoGContactsStrategy;
import com.sun.electric.tool.routing.SeaOfGates;
import com.sun.electric.tool.routing.seaOfGates.SeaOfGatesEngine.RouteResolution;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.util.math.FixpCoord;
import com.sun.electric.util.math.Orientation;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Factory class that provides #SeaOfGatesEngin.Handler's .
 */
public class SeaOfGatesHandlers {

	/** true to use mutable database access instead of immutable. */	private static final boolean USEMUTABLE = false;

	/**
     * Save mode determines how to save changes.
     */
    public enum Save {
        SAVE_ONCE, SAVE_PERIODIC, SAVE_SNAPSHOTS
    };
    /** Default save mode. */
    private static final Save SAVE_DEFAULT = Save.SAVE_PERIODIC;

    /** Instances of class are not allowed */
    private SeaOfGatesHandlers() {
    }

    /**
     * Start routine in a Job with default save and save arcs modes
     * @param cell Cell to route
     * @param selected ArcInsts to route or null to route all unrouted arcs in the Cell
     * @param version version of SeaOfGatesEngine
     */
    public static void startInJob(Cell cell, Collection<ArcInst> selected, SeaOfGatesEngineFactory.SeaOfGatesEngineType version) {
        startInJob(cell, selected, version, SAVE_DEFAULT);
    }

    /**
     * Start routine in a Job with default save and save arcs modes
     * @param cell Cell to route
     * @param selected ArcInsts to route or null to route all unrouted arcs in the Cell
     * @param version version of SeaOfGatesEngine
     * @param save mode to save changes
     */
    public static void startInJob(Cell cell, Collection<ArcInst> selected, SeaOfGatesEngineFactory.SeaOfGatesEngineType version,
            Save save) {
        // Run seaOfGatesRoute on selected unrouted arcs
        // do the routing in a separate job
        new SeaOfGatesJob(cell, selected, version, save).startJob();
    }

    /**
     * Returns Job handler with default Save mode and default SaveArcs mode
     * @param job executing Job or null to save in raw database
     * @param ep EditingPreferences
     */
    public static SeaOfGatesEngine.Handler getDefault(Cell cell, String resultCellName, SoGContactsStrategy contactPlacementAction, Job job, EditingPreferences ep) {
        return getDefault(cell, resultCellName, contactPlacementAction, job, ep, SAVE_DEFAULT);
    }

    /**
     * Returns Job handler with default Save mode and specified SaveArcs mode
     * @param job executing Job or null to save in raw database
     * @param ep EditingPreferences
     * @param save specified Save mode
     */
    public static SeaOfGatesEngine.Handler getDefault(Cell cell, String resultCellName, SoGContactsStrategy contactPlacementAction, Job job, EditingPreferences ep, Save save) {
    	if (USEMUTABLE)
    	{
	    	// if placing in separate cell, use mutable engine
	    	if (resultCellName != null)
	            return new MutableSeaOfGatesHook(cell, resultCellName, contactPlacementAction, job, ep);
    	}

        return new DefaultSeaOfGatesHook(cell, resultCellName, contactPlacementAction, job, ep, save);
    }

    /**
     * Returns dummy handler
     */
    public static SeaOfGatesEngine.Handler getDummy(EditingPreferences ep, PrintStream out) {
        return new DummySeaOfGatesHandler(ep, out);
    }

    /**
     * Class to run sea-of-gates routing in a separate Job.
     */
    private static class SeaOfGatesJob extends Job {

        private final Cell cell;
        private final int[] arcIdsToRoute;
        private final SeaOfGates.SeaOfGatesOptions prefs = new SeaOfGates.SeaOfGatesOptions();
        private final SeaOfGatesEngineFactory.SeaOfGatesEngineType version;
        private final Save save;

        protected SeaOfGatesJob(Cell cell, Collection<ArcInst> arcsToRoute, SeaOfGatesEngineFactory.SeaOfGatesEngineType version,
                Save save) {
            super("Sea-Of-Gates Route", Routing.getRoutingTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            this.cell = cell;
            if (arcsToRoute != null) {
                arcIdsToRoute = new int[arcsToRoute.size()];
                Iterator<ArcInst> it = arcsToRoute.iterator();
                for (int i = 0; i < arcsToRoute.size(); i++) {
                    arcIdsToRoute[i] = it.next().getArcId();
                }
            } else {
                arcIdsToRoute = null;
            }
            prefs.getOptionsFromPreferences(false);
            this.version = version;
            this.save = save;
        }

        @Override
        public boolean doIt() throws JobException {
            SeaOfGatesEngine router = SeaOfGatesEngineFactory.createSeaOfGatesEngine(version);
            router.setPrefs(prefs);
            Layout.changesQuiet(true);
            SeaOfGatesEngine.Handler handler = getDefault(cell, router.getPrefs().resultCellName, router.getPrefs().contactPlacementAction, this, getEditingPreferences(), save);
            if (arcIdsToRoute != null) {
                List<ArcInst> arcsToRoute = new ArrayList<ArcInst>();
                for (int arcId : arcIdsToRoute) {
                    arcsToRoute.add(cell.getArcById(arcId));
                }
                router.routeIt(handler, cell, false, arcsToRoute);
            } else {
                router.routeIt(handler, cell, false);
            }
            return true;
        }

        @Override
        public void showSnapshot() {
            if (save != Save.SAVE_ONCE) {
                super.showSnapshot();
            }
        }
    }

	static class ContactTemplate
	{
		final CellId cellId;
		Cell cell;
		final Orientation orient;
		final ExportId exportId;

		ContactTemplate(CellId cellId, Orientation orient, ExportId exportId)
		{
			this.cellId = cellId;
			this.cell = null;
			this.orient = orient;
			this.exportId = exportId;
		}

		Cell getCell(Library lib)
		{
			if (cell == null)
			{
				for(Iterator<Cell> it = lib.getCells(); it.hasNext(); )
				{
					Cell c = it.next();
					if (c.getId() == cellId)
					{
						cell = c;
						break;
					}
				}
			}
			return cell;
		}
	}

	static class ContactKey
	{
		final PrimitiveNode pNp;
		final EPoint size;
		final int techBits;

		ContactKey(PrimitiveNode pNp, EPoint size, int techBits)
		{
			if (pNp == null || size == null) throw new NullPointerException();
			this.pNp = pNp;
			this.size = size;
			this.techBits = techBits;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o instanceof ContactKey)
			{
				ContactKey that = (ContactKey) o;
				return this.pNp.equals(that.pNp) && this.size.equals(that.size) && this.techBits == that.techBits;
			}
			return false;
		}

		@Override
		public int hashCode()
		{
			return 89 * pNp.hashCode() + size.hashCode() + techBits;
		}

		@Override
		public String toString() {
			assert(pNp instanceof PrimitiveNode);

			PrimitiveNode pn = (PrimitiveNode)pNp;
			double scale = pn.getTechnology().getScale();

			double[] xExt = new double[2];
			double[] yExt = new double[2];
			double[] viaSize = new double[2];
			double[] viaSpacing = new double[2];
			String[] names = new String[2];
			int count = 0;

			for (Technology.NodeLayer node : pn.getNodeLayers())
			{
				Layer l = node.getLayer();

				if (l.getFunction().isContact())
				{
					// via size
					viaSize[0] = (FixpCoord.fixpToLambda(node.getMulticutSizeX().getFixp())); // x value
					viaSize[1] = (FixpCoord.fixpToLambda(node.getMulticutSizeY().getFixp())); // y value

					// via spacing
					// @TODO assuming 1x1 cuts
					viaSpacing[0] = (FixpCoord.fixpToLambda(node.getMulticutSep1D().getFixp())); // x value
					viaSpacing[1] = (FixpCoord.fixpToLambda(node.getMulticutSep1D().getFixp())); // y value
					continue;
				}
				String tmp = l.getName();
				assert(count < 2);

				// two potential substitutions
				tmp = tmp.replaceAll("_MASK_1", "CA");
				tmp = tmp.replaceAll("_MASK_2", "CB");
				names[(count+1)%2] = tmp; // the upper layer must be first in names. First layer is always the bottom
				EdgeH leftEdge = node.getLeftEdge();
				EdgeH rightEdge = node.getRightEdge();
				EdgeV topEdge = node.getTopEdge();
				EdgeV bottomEdge = node.getBottomEdge();
				long portLowX = leftEdge.getFixpValue(size);
				long portHighX = rightEdge.getFixpValue(size);
				long portLowY = bottomEdge.getFixpValue(size);
				long portHighY = topEdge.getFixpValue(size);
				xExt[count] = FixpCoord.fixpToLambda(portHighX-portLowX);
				yExt[count] = FixpCoord.fixpToLambda(portHighY-portLowY);
				count++;
			}

			// do extra process with vias once values have been identified
			for (int i = 0; i < 2; i++)
			{
				xExt[i] = (xExt[i] - viaSize[0])*scale/2;
				yExt[i] = (yExt[i] - viaSize[1])*scale/2;
			}

			// using -1 as number of fractions to force zero digits
			String newName = names[0] + "_" + names[1] + "_"; // first the upper in names
			newName += "X_"
					+ TextUtils.formatDouble(viaSize[0]*scale, -1) + "_" + TextUtils.formatDouble(viaSize[1]*scale, -1)
					+ "_1_1_"
					+ TextUtils.formatDouble(viaSpacing[0]*scale, -1) + "_" + TextUtils.formatDouble(viaSpacing[1]*scale, -1) + "_"
					+ TextUtils.formatDouble(xExt[0], -1) + "_" + TextUtils.formatDouble(yExt[0], -1) + "_"
					+ TextUtils.formatDouble(xExt[1], -1) + "_" + TextUtils.formatDouble(yExt[1], -1);
			return newName;
		}

		CellName getDefaultCellName() {
			return CellName.parseName(this + ";1{lay}");
		}
	}

    private static class DefaultSeaOfGatesHook implements SeaOfGatesEngine.Handler {

        private final EDatabase database;
        private final Job job;
        private final EditingPreferences ep;
        private final Save save;
        private final SeaOfGatesCellBuilder cellBuilder;
        private final UserInterface ui = Job.getUserInterface();
        private int periodicCounter;

        private DefaultSeaOfGatesHook(Cell cell, String resultCellName, SoGContactsStrategy contactPlacementAction, Job job, EditingPreferences ep, Save save) {
            this.database = cell.getDatabase();
            this.job = job;
            this.ep = ep;
            this.save = save;
            cellBuilder = new SeaOfGatesCellBuilder(database.backup(), cell.getId(), resultCellName, contactPlacementAction, ep);
            periodicCounter = 0;
        }

        /**
         * Returns EditingPreferences
         * @return EditingPreferences
         */
        @Override
        public EditingPreferences getEditingPreferences() {
            return ep;
        }

        /**
         * Check if we are scheduled to abort. If so, print message if non null and
         * return true.
         * @return true on abort, false otherwise. If job is scheduled for abort or
         *         aborted. and it will report it to standard output
         */
        @Override
        public boolean checkAbort() {
            return job != null && job.checkAbort();
        }

        /**
         * Log a message at the TRACE level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void trace(String msg) {
            printMessage(msg, true);
        }

        /**
         * Log a message at the DEBUG level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void debug(String msg) {
            if (Job.getDebug()) {
                printMessage(msg, true);
            }
        }

        /**
         * Log a message at the INFO level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void info(String msg) {
            printMessage(msg, true);
        }

        /**
         * Log a message at the WARN level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void warn(String msg) {
            printMessage("WARNING: " + msg, true);
        }

        /**
         * Log a message at the ERROR level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void error(String msg) {
            printMessage("ERROR: " + msg, true);
        }

        private void printMessage(String s, boolean newLine) {
            if (newLine) {
                System.out.println(s);
            } else {
                System.out.print(s);
            }
        }

        /**
         * Method called when all errors are logged.  Initializes pointers for replay of errors.
         */
        @Override
        public void termLogging(ErrorLogger errorLogger) {
            errorLogger.termLogging(true);
        }

        /**
         * Method to start the display of a progress dialog.
         * @param msg the message to show in the progress dialog.
         */
        @Override
        public void startProgressDialog(String msg) {
            ui.startProgressDialog(msg, null);
        }

        /**
         * Method to stop the progress bar
         */
        @Override
        public void stopProgressDialog() {
            ui.stopProgressDialog();
        }

        /**
         * Method to set a text message in the progress dialog.
         * @param message the new progress message.
         */
        @Override
        public void setProgressNote(String message) {
            ui.setProgressNote(message);
            if (Job.getDebug())
            	System.out.println(message);
        }

        /**
         * Method to update the progress bar
         * @param pct the percentage done (from 0 to 100).
         */
        @Override
        public void setProgressValue(long done, long total) {
        	int val = (int) (done * 100 / total);
            ui.setProgressValue(val);
        }

        /**
         * Method to instantiate RouteResolution
         * Can be called from any thread.
         * @param resolution RouteResolution
         */
        @Override
        public void instantiate(RouteResolution resolution) {
            cellBuilder.instantiate(resolution);
        }

		/**
		 * Method to tell the name of the cell where routing results are stored.
		 * If null, results are placed back in the original cell.
		 * @return the name of the routing results cell.
		 */
		public String getRoutingCellName()
		{
			return cellBuilder.resultCellName;
		}

		/**
         * flush changes
         * @param force unconditionally perform the final flush
         * Can be called only from database thread
         */
        @Override
        public void flush(boolean force) {
        	switch (save)
        	{
        		case SAVE_SNAPSHOTS:
        			force = true;
        			break;
        		case SAVE_PERIODIC:
        			if (periodicCounter++ > 100)
        			{
        				periodicCounter = 0;
        				force = true;
        			}
        			break;
        		case SAVE_ONCE:
        			break;
        	}
            if (force) {
                Snapshot snapshot = cellBuilder.commit();

                database.checkChanging();
                database.lowLevelSetCanUndoing(true);
                database.undo(snapshot);
                database.lowLevelSetCanUndoing(false);
                database.getCell(cellBuilder.cellId).getLibrary().setChanged();
                if (job instanceof SeaOfGatesJob) {
                    ((SeaOfGatesJob) job).showSnapshot();
                }
            }
        }
    }

	/**
	 * Alternate handler that doesn't use the immutable database
	 */
	private static class MutableSeaOfGatesHook implements SeaOfGatesEngine.Handler {

		private final Cell originalCell;
		private final Cell cell;
		private final String resultCellName;
		private final SoGContactsStrategy contactPlacementAction;
		private final Job job;
		private final EditingPreferences ep;
		private final UserInterface ui = Job.getUserInterface();
		private Map<ContactKey,ContactTemplate> contactTemplates = new HashMap<ContactKey,ContactTemplate>();

		private MutableSeaOfGatesHook(Cell cell, String resultCellName, SoGContactsStrategy contactPlacementAction, Job job, EditingPreferences ep) {
			originalCell = cell;
			if (resultCellName == null) this.cell = cell; else
			{
				// create separate cell for results
				this.cell = Cell.makeInstance(ep, cell.getLibrary(), resultCellName + "{lay}");

				// instantiate results cell in original cell
				NodeInst.makeInstance(this.cell, ep, new Point2D.Double(0, 0), 0, 0, originalCell);
			}
			this.resultCellName = resultCellName;
			this.contactPlacementAction = contactPlacementAction;
			this.job = job;
			this.ep = ep;
		}

		/**
		 * Returns EditingPreferences
		 * @return EditingPreferences
		 */
		@Override
		public EditingPreferences getEditingPreferences() { return ep; }

		/**
		 * Check if we are scheduled to abort. If so, print message if non null and return true.
		 * @return true on abort, false otherwise. If job is scheduled for abort or
		 *		 aborted. and it will report it to standard output
		 */
		@Override
		public boolean checkAbort() {
			return job != null && job.checkAbort();
		}

		/**
		 * Log a message at the TRACE level.
		 * @param msg the message string to be logged
		 */
		@Override
		public void trace(String msg) { printMessage(msg, true); }

		/**
		 * Log a message at the DEBUG level.
		 * @param msg the message string to be logged
		 */
		@Override
		public void debug(String msg) {
			if (Job.getDebug()) {
				printMessage(msg, true);
			}
		}

		/**
		 * Log a message at the INFO level.
		 * @param msg the message string to be logged
		 */
		@Override
		public void info(String msg) { printMessage(msg, true); }

		/**
		 * Log a message at the WARN level.
		 * @param msg the message string to be logged
		 */
		@Override
		public void warn(String msg) { printMessage("WARNING: " + msg, true); }

		/**
		 * Log a message at the ERROR level.
		 * @param msg the message string to be logged
		 */
		@Override
		public void error(String msg) { printMessage("ERROR: " + msg, true); }

		private void printMessage(String s, boolean newLine) {
			if (newLine) {
				System.out.println(s);
			} else {
				System.out.print(s);
			}
		}

		/**
		 * Method called when all errors are logged.  Initializes pointers for replay of errors.
		 */
		@Override
		public void termLogging(ErrorLogger errorLogger) { errorLogger.termLogging(true); }

		/**
		 * Method to start the display of a progress dialog.
		 * @param msg the message to show in the progress dialog.
		 */
		@Override
		public void startProgressDialog(String msg) { ui.startProgressDialog(msg, null); }

		/**
		 * Method to stop the progress bar
		 */
		@Override
		public void stopProgressDialog() { ui.stopProgressDialog(); }

		/**
		 * Method to set a text message in the progress dialog.
		 * @param message the new progress message.
		 */
		@Override
		public void setProgressNote(String message) {
			ui.setProgressNote(message);
			if (Job.getDebug())
				System.out.println(message);
		}

		/**
		 * Method to update the progress bar
		 * @param pct the percentage done (from 0 to 100).
		 */
		@Override
		public void setProgressValue(long done, long total) {
			int val = (int) (done * 100 / total);
			ui.setProgressValue(val);
		}

		/**
		 * Method to instantiate RouteResolution.
		 * @param resolution RouteResolution
		 */
		@Override
		public void instantiate(RouteResolution resolution) {
			for(Integer aiKillID : resolution.arcsIDsToKill)
			{
				ArcInst aiKill = originalCell.getArcById(aiKillID.intValue());
				if (aiKill != null && aiKill.isLinked()) aiKill.kill();
			}
			for(Integer niKillID : resolution.nodesIDsToKill)
			{
				NodeInst niKill = originalCell.getNodeById(niKillID.intValue());
				niKill.kill();
			}

			// if placing contacts in subcells, create them now
			if (contactPlacementAction != SoGContactsStrategy.SOGCONTACTSATTOPLEVEL)
			{
				for (SeaOfGatesEngine.RouteNode rn : resolution.nodesToRoute)
				{
					if (rn.exists()) continue;
					PrimitiveNode pNp = (PrimitiveNode)rn.getProto();
					if (!pNp.getFunction().isContact()) continue;
					ContactTemplate contactTemplate = getTemplateForContact(pNp, rn.getSize(), rn.getTechBits());
					if (contactTemplate == null)
					{
						makeTemplateForContact(pNp, rn.getSize(), rn.getTechBits());
					}
				}
			}

			for(SeaOfGatesEngine.RouteNode rn : resolution.nodesToRoute)
			{
				if (!rn.exists())
				{
					NodeProto np = rn.getProto();
					Orientation orient = rn.getOrient();
					if (np.getFunction().isContact())
					{
						PrimitiveNode pNp = (PrimitiveNode)np;
						ContactTemplate contactTemplate = getTemplateForContact(pNp, rn.getSize(), rn.getTechBits());
						if (contactTemplate != null)
						{
							// place cell "c" instead of primitive "protoId"
							np = contactTemplate.getCell(cell.getLibrary());
							orient = contactTemplate.orient;
						}
					}

					NodeInst ni = null;
					if (resultCellName == null || !(np instanceof PrimitiveNode))
					{
						ni = NodeInst.makeInstance(np, ep, rn.getLoc(), rn.getWidth(), rn.getHeight(), cell, orient, null, rn.getTechBits());
					} else
					{
						NodeInst dummyNi = NodeInst.makeDummyInstance(np, ep, rn.getTechBits(), rn.getLoc(), rn.getWidth(), rn.getHeight(), orient);
						Poly[] polys = np.getTechnology().getShapeOfNode(dummyNi);
						for(int i=0; i<polys.length; i++)
						{
							Poly poly = polys[i];
							PrimitiveNode pNp = poly.getLayer().getPureLayerNode();
							Rectangle2D bounds = poly.getBounds2D();
							Point2D ctr = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
							NodeInst niPoss = NodeInst.makeInstance(pNp, ep, ctr, bounds.getWidth(), bounds.getHeight(), cell);
							if (ni == null) ni = niPoss;
						}
					}
					if (ni != null)
					{
						rn.setPi(ni.getOnlyPortInst());
						rn.setTapConnection(ni.getD());
					}
				}
			}
			for(SeaOfGatesEngine.RouteArc ra : resolution.arcsToRoute)
			{
				if (ra.getHead().getPi() == null || ra.getTail().getPi() == null) continue;
				if (resultCellName == null)
				{
					// place arc in original cell
					ArcInst ai = ArcInst.makeInstanceBase(ra.getProto(), ep, ra.getWidth(), ra.getHead().getPi(), ra.getTail().getPi(),
						ra.getHead().getPi().getCenter(), ra.getTail().getPi().getCenter(), ra.getName());
					if (ai == null)
						System.out.println("****FAILED TO ROUTE ARC "+ra.getProto().describe());
				} else
				{
					// place polygons for the arc in the result cell
					EPoint headPt = ra.getHead().getPi().getCenter();
					EPoint tailPt = ra.getTail().getPi().getCenter();
					double lX = Math.min(headPt.getX(), tailPt.getX()) - ra.getWidth()/2;
					double hX = Math.max(headPt.getX(), tailPt.getX()) + ra.getWidth()/2;
					double lY = Math.min(headPt.getY(), tailPt.getY()) - ra.getWidth()/2;
					double hY = Math.max(headPt.getY(), tailPt.getY()) + ra.getWidth()/2;
					ArcLayer[] arcLayers = ra.getProto().getArcLayers();
					PrimitiveNode pNp = arcLayers[0].getLayer().getPureLayerNode();
					Point2D ctr = new Point2D.Double((lX+hX)/2, (lY+hY)/2);
					NodeInst.makeInstance(pNp, ep, ctr, hX-lX, hY-lY, cell);
				}
			}

			// if routing into original cell, add unrouted arcs that didn't get routed
			if (resultCellName == null)
			{
				for(SeaOfGatesEngine.RouteAddUnrouted rau : resolution.unroutedToAdd.keySet())
				{
					String name = resolution.unroutedToAdd.get(rau);
					PortInst piA = rau.getTailPort();
					PortInst piB = rau.getHeadPort();
					ArcInst.makeInstance(Generic.tech().unrouted_arc, ep, piA, piB, piA.getCenter(), piB.getCenter(), name);
				}
			}
		}

		/**
		 * Method to find a contact template to describe a given primitive node, when encapsulated
		 * @param pNp the contact to package.
		 * @param size the size of the contact.
		 * @param techBits technology bits of the contact
		 * @return a ContactTemplate that describes that packaged contact.
		 */
		ContactTemplate getTemplateForContact(PrimitiveNode pNp, EPoint size, int techBits)
		{
			ContactKey contactKey = new ContactKey(pNp, size, techBits);
			return contactTemplates.get(contactKey);
		}

		private boolean doesCellMatch(Cell contactCell, Poly[] shapeNodes)
		{
			BitSet matched = new BitSet();
			for(Iterator<NodeInst> it = contactCell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance()) continue;
				PrimitiveNode pn = (PrimitiveNode)ni.getProto();
				if (pn.getTechnology() == Generic.tech()) continue;
				if (pn.getFunction() != PrimitiveNode.Function.NODE) return false;
				if (ni.getTrace() != null) return false;

				// get layer of this pure-layer node
				Poly[] pureLayers = pn.getTechnology().getShapeOfNode(ni);
				Layer lay = pureLayers[0].getLayer();

				boolean foundMatch = false;
				for(int i=0; i<shapeNodes.length; i++)
				{
					if (matched.get(i)) continue;
					Poly sn = shapeNodes[i];
					if (lay != sn.getLayer()) continue;
					Rectangle2D bounds = sn.getBounds2D();
					if (ni.getXSize() != bounds.getWidth() || ni.getYSize() != bounds.getHeight()) continue;
					if (ni.getAnchorCenterX() != bounds.getCenterX() || ni.getAnchorCenterY() != bounds.getCenterY()) continue;
					matched.set(i);
					foundMatch = true;
					break;
				}
				if (!foundMatch)
					return false;
			}
			return matched.cardinality() == shapeNodes.length;
		}

		/**
		 * Method to create a CellId to describe a given primitive node, when encapsulated
		 * @param pNp the contact to package.
		 * @param size the size of the contact.
		 * @param techBits technology bits of the contact
		 * @return a ContactTemplate that describes that packaged contact.
		 */
		private void makeTemplateForContact(PrimitiveNode pNp, EPoint size, int techBits)
		{
			assert pNp.getFunction().isContact();
			ContactKey contactKey = new ContactKey(pNp, size, techBits);
			Library lib = cell.getLibrary();

			Cell contactCell = null;
			Orientation contactTemplateOrientation = Orientation.IDENT;
			if (contactPlacementAction != SoGContactsStrategy.SOGCONTACTSFORCESUBCELLS)
			{
				NodeInst ni = NodeInst.makeDummyInstance(pNp, ep, EPoint.ORIGIN, size.getX(), size.getY(), Orientation.IDENT);
				Poly[] conShape = pNp.getTechnology().getShapeOfNode(ni);

				NodeInst niRot = NodeInst.makeDummyInstance(pNp, ep, EPoint.ORIGIN, size.getX(), size.getY(), Orientation.R);
				Poly[] conShapeRot = pNp.getTechnology().getShapeOfNode(niRot);

				for (Iterator<Cell> it = lib.getCells(); it.hasNext(); )
				{
					Cell cell = it.next();
					if (doesCellMatch(cell, conShape))
					{
						contactCell = cell;
						break;
					}
					if (doesCellMatch(cell, conShapeRot))
					{
						contactCell = cell;
						contactTemplateOrientation = Orientation.R;
						break;
					}
				}
			}

			if (contactCell != null)
			{
				// Contact cell found, make sure it has an export
				if (!contactCell.getExports().hasNext())
				{
					// no exports in contact cell: add one
					pNp = Generic.tech().universalPinNode;
					NodeInst ni = NodeInst.makeInstance(pNp, ep, EPoint.ORIGIN, pNp.getDefaultLambdaBaseWidth(ep),
						pNp.getDefaultLambdaBaseHeight(ep), contactCell);
					Export.newInstance(contactCell, ni.getOnlyPortInst(), "port", ep);
				}

				// remember this contact cell
				ExportId eId = contactCell.getExports().next().getId();
				ContactTemplate contactTemplate = new ContactTemplate(contactCell.getId(), contactTemplateOrientation, eId);
				contactTemplates.put(contactKey, contactTemplate);
				return;
			}

			// no contact cell found. Okay if not needed
			if (contactPlacementAction == SoGContactsStrategy.SOGCONTACTSUSEEXISTINGSUBCELLS) return;

			// Create a new contact cell, use new name strategy here
			contactCell = Cell.makeInstance(ep, lib, contactKey.getDefaultCellName().getName());

			// create geometry
			NodeInst ni = NodeInst.makeDummyInstance(pNp, ep, EPoint.ORIGIN, size.getX()+pNp.getDefWidth(ep),
				size.getY()+pNp.getDefHeight(ep), contactTemplateOrientation);
			Poly[] conShape = pNp.getTechnology().getShapeOfNode(ni);
			for(int i=0; i<conShape.length; i++)
			{
				Poly shape = conShape[i];
				Layer lay = shape.getLayer();
				PrimitiveNode pureNp = lay.getPureLayerNode();
				Rectangle2D bounds = shape.getBounds2D();
				Point2D ctr = new Point2D.Double(bounds.getCenterX(), bounds.getCenterY());
				NodeInst.makeInstance(pureNp, ep, ctr, bounds.getWidth(), bounds.getHeight(), contactCell);
			}

			// Create export
			pNp = Generic.tech().universalPinNode;
			ni = NodeInst.makeInstance(pNp, ep, EPoint.ORIGIN, pNp.getDefaultLambdaBaseWidth(ep), pNp.getDefaultLambdaBaseHeight(ep), contactCell);
			Export e = Export.newInstance(contactCell, ni.getOnlyPortInst(), "port", ep);

			// save ContactTemplate
			ContactTemplate contactTemplate = new ContactTemplate(contactCell.getId(), contactTemplateOrientation, e.getId());
			contactTemplates.put(contactKey, contactTemplate);
		}

		/**
		 * Method to tell the name of the cell where routing results are stored.
		 * If null, results are placed back in the original cell.
		 * @return the name of the routing results cell.
		 */
		public String getRoutingCellName() { return resultCellName; }

		/**
		 * flush changes
		 * @param force unconditionally perform the final flush
		 * Can be called only from database thread
		 */
		@Override
		public void flush(boolean force) {}
	}

    private static class DummySeaOfGatesHandler implements SeaOfGatesEngine.Handler {

        private final EditingPreferences ep;
        private final PrintStream out;

        private DummySeaOfGatesHandler(EditingPreferences ep, PrintStream out) {
            this.ep = ep;
            this.out = out;
        }

        /**
         * Returns EditingPreferences
         * @return EditingPreferences
         */
        @Override
        public EditingPreferences getEditingPreferences() {
            return ep;
        }

        /**
         * Check if we are scheduled to abort. If so, print message if non null and
         * return true.
         * @return true on abort, false otherwise. If job is scheduled for abort or
         *         aborted. and it will report it to standard output
         */
        @Override
        public boolean checkAbort() {
            return false;
        }

        /**
         * Log a message at the TRACE level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void trace(String msg) {
        }

        /**
         * Log a message at the DEBUG level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void debug(String msg) {
            out.println(msg);
        }

        /**
         * Log a message at the INFO level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void info(String msg) {
            out.println(msg);
        }

        /**
         * Log a message at the WARN level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void warn(String msg) {
            out.println("WARN: " + msg);
        }

        /**
         * Log a message at the ERROR level.
         *
         * @param msg the message string to be logged
         */
        @Override
        public void error(String msg) {
            out.println("ERROR: " + msg);
        }

        /**
         * Method called when all errors are logged.  Initializes pointers for replay of errors.
         */
        @Override
        public void termLogging(ErrorLogger errorLogger) {
        }

        /**
         * Method to start the display of a progress dialog.
         * @param msg the message to show in the progress dialog.
         */
        @Override
        public void startProgressDialog(String msg) {
        }

        /**
         * Method to stop the progress bar
         */
        @Override
        public void stopProgressDialog() {
        }

        /**
         * Method to set a text message in the progress dialog.
         * @param message the new progress message.
         */
        @Override
        public void setProgressNote(String message) {
        }

        /**
         * Method to update the progress bar
         * @param pct the percentage done (from 0 to 100).
         */
        @Override
        public void setProgressValue(long done, long total) {
        }

        /**
         * Method to instantiate RouteResolution.
         * Can be called from any thread.
         * @param resolution RouteResolution
         */
        @Override
        public void instantiate(RouteResolution resolution) {
        }

		/**
		 * Method to tell the name of the cell where routing results are stored.
		 * If null, results are placed back in the original cell.
		 * @return the name of the routing results cell.
		 */
        @Override
		public String getRoutingCellName()
		{
			return null;
		}

        /**
         * flush changes
         * Can be called only from database thread
         * @param force unconditionally perform the final flush
         */
        @Override
        public void flush(boolean force) {
        }
    }
}
