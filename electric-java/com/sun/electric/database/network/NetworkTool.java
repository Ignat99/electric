/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: NetworkTool.java
 * Written by: Dmitry Nadezhin.
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
package com.sun.electric.database.network;

import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.Pref;
import com.sun.electric.database.text.Setting;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Geometric;
import com.sun.electric.database.topology.IconNodeInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.ToolSettings;
import com.sun.electric.tool.user.User;

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

/**
 * This is the Network tool.
 */
public class NetworkTool extends Tool {

    /**
     * Signals that a method has been invoked at an illegal or
     * inappropriate time.  In other words, the Java environment or
     * Java application is not in an appropriate state for the requested
     * operation.
     */
    public static class NetlistNotReady extends RuntimeException {

        /**
         * Constructs an IllegalStateException with no detail message.
         * A detail message is a String that describes this particular exception.
         */
        public NetlistNotReady() {
            super("User netlist is not ready");
        }

        /**
         * Constructs an IllegalStateException with the specified detail
         * message.  A detail message is a String that describes this particular
         * exception.
         *
         * @param s the String that contains a detailed message
         */
        public NetlistNotReady(String s) {
            super(s);
        }
    }

    /**
     * Method to renumber the netlists.
     */
    public static void renumberNetlists() {
        new RenumberJob();
    }

    private static class RenumberJob extends Job {

        private RenumberJob() {
            super("Renumber All Networks", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
            startJob();
        }

        public boolean doIt() throws JobException {
//            EDatabase.serverDatabase().getNetworkManager().redoNetworkNumbering(true);
            return true;
        }
    }
    // ---------------------- private and protected methods -----------------
    /** the Network tool. */
    private static final NetworkTool tool = new NetworkTool();
    /** flag for debug print. */
    static boolean debug = false;
    /** flag for information print. */
    static boolean showInfo = true;
    /** total number of errors for statistics */
    public static int totalNumErrors = 0;
    /** sort keys for sorting network errors */
    static final int errorSortNetworks = 0;
    static final int errorSortNodes = 1;
    static final int errorSortPorts = 2;

    /**
     * The constructor sets up the Network tool.
     */
    public NetworkTool() {
        super("network");
    }

    public static NetworkTool getNetworkTool() {
        return tool;
    }

    /**
     * Method to set the level of information that is displayed.
     * When libraries are being read "quietly", no information should be output.
     * @param infoOutput true for normal information output, false for quiet.
     */
    public static void setInformationOutput(boolean infoOutput) {
        showInfo = infoOutput;
    }

    /****************************** PUBLIC METHODS ******************************/
    /**
     * Method to retrieve all networks for a portInst.
     * Used by Highlighter and Connection
     * @param pi the PortInst being considered.
     * @param netlist the netlist being searched.
     * @param nets a set into which all found networks will be added.
     * @return set the set of found networks.
     */
    public static Set<Network> getNetworksOnPort(PortInst pi, Netlist netlist, Set<Network> nets) {
//        boolean added = false;
        if (nets == null) {
            nets = new HashSet<Network>();
        }
        if (!pi.isLinked()) {
            return nets;
        }

        NodeInst ni = pi.getNodeInst();
        PortProto pp = pi.getPortProto();
        if (ni instanceof IconNodeInst)
        {
            int busWidth = pp.getNameKey().busWidth();
            for (int arrayIndex = 0, numNodables = ni.getNumNodables(); arrayIndex < numNodables; arrayIndex++)
            {
                Nodable no = ni.getNodable(arrayIndex);
                for (int busIndex = 0; busIndex < busWidth; busIndex++)
                {
                    Network net = netlist.getNetwork(no, pp.getNameKey().subname(busIndex));
                    if (net != null)
                    {
                        nets.add(net);
                    }
                }
            }
        } else
        {
            for (int busIndex = 0, busWidth = netlist.netCell.getBusWidth(ni, pp); busIndex < busWidth; busIndex++) {
                    Network net = netlist.getNetwork(ni, pp, busIndex);
                    if (net != null)
                    {
                        nets.add(net);
                    }
            }
        }
        return nets;
//        for (Iterator<Connection> aIt = pi.getConnections(); aIt.hasNext();) {
//            Connection con = aIt.next();
//            ArcInst ai = con.getArc();
//            int wid = netlist.getBusWidth(ai);
//            for (int i = 0; i < wid; i++) {
//                Network net = netlist.getNetwork(ai, i);
//                if (net != null) {
//                    added = true;
//                    nets.add(net);
//                }
//            }
//        }
//        if (!added) {
//            // port may be exported, without wire attached, and may
//            // connect by export name to other wires
//            NodeInst ni = pi.getNodeInst();
//            Set<PortInst> ports = new HashSet<PortInst>();
//            ports.add(pi);
//            for (Iterator<PortInst> it = ni.getPortInsts(); it.hasNext();) {
//                // several ports on node may be connected together at lower level
//                PortInst otherpi = it.next();
//                if (otherpi == pi) {
//                    continue;
//                }
//                if (netlist.sameNetwork(ni, pi.getPortProto(), ni, otherpi.getPortProto())) {
//                    ports.add(otherpi);
//                }
//            }
//            for (Iterator<Export> it = ni.getParent().getExports(); it.hasNext();) {
//                Export export = it.next();
//                if (ports.contains(export.getOriginalPort())) {
//                    Name name = export.getNameKey();
//                    for (int i = 0; i < name.busWidth(); i++) {
//                        nets.add(netlist.getNetwork(pi.getNodeInst(), pi.getPortProto(), i));
//                        added = true;
//                    }
//                    break;
//                }
//            }
//        }
//        if (!added) {
//            PortProto pp = pi.getPortProto();
//            if (pp instanceof Export) {
//                int wid = netlist.getBusWidth((Export) pp);
//                for (int i = 0; i < wid; i++) {
//                    Network net = netlist.getNetwork(pi.getNodeInst(), pp, i);
//                    if (net != null) {
//                        nets.add(net);
//                    }
//                }
//            } else {
//                Network net = netlist.getNetwork(pi);
//                if (net != null) {
//                    nets.add(net);
//                }
//            }
//        }
//        return nets;
    }

    /**
     * Method to retrieve all networks on a Geometric object.
     * @param geom the Geometric being considered.
     * @param netlist the netlist being searched.
     * @param nets a set into which all found networks will be added.
     * @return set the set of found networks.
     */
    public static Set<Network> getNetworks(Geometric geom, Netlist netlist, Set<Network> nets) {
        if (nets == null) {
            nets = new HashSet<Network>();
        } else {
            nets.clear();
        }

        if (geom instanceof ArcInst) {
            nets.add(netlist.getNetwork((ArcInst) geom, 0));
        } else {
            NodeInst ni = (NodeInst) geom;
            for (Iterator<PortInst> pIt = ni.getPortInsts(); pIt.hasNext();) {
                PortInst pi = pIt.next();
                nets = getNetworksOnPort(pi, netlist, nets);
                //nets.add(netlist.getNetwork(ni, pi.getPortProto(), 0));
                //nets.add(netlist.getNetwork(pi));
            }
        }
        return nets;
    }

    /**
     * Method to initialize a tool.
     */
    public void init() {
        setOn();
        if (!debug) {
            return;
        }
        System.out.println("NetworkTool.init()");
    }

    /************************* PROJECT PREFERENCES **************************/
    /**
     * Method to tell whether resistors are ignored in the circuit.
     * When ignored, they appear as a "short", connecting the two sides.
     * When included, they appear as a component with different networks on either side.
     * @return true if resistors are ignored in the circuit.
     */
    public static boolean isIgnoreResistors() {
        return getIgnoreResistorsSetting().getBoolean();
    }

    private static Netlist.ShortResistors isIgnoreResistors_() {
        return Netlist.ShortResistors.NO;
    }

    /**
     * Returns project preferences to tell whether resistors are ignored in the circuit.
     * When ignored, they appear as a "short", connecting the two sides.
     * When included, they appear as a component with different networks on either side.
     * Returns project preferences to tell whether resistors are ignored in the circuit.
     */
    public static Setting getIgnoreResistorsSetting() {
        return ToolSettings.getIgnoreResistorsSetting();
    }
    /****************************** OPTIONS ******************************/
    private final static boolean BUS_ASCENDING_DEFAULT = false;
    private static Pref cacheBusAscending = Pref.makeBooleanServerPref("BusAscending", NetworkTool.tool.prefs, BUS_ASCENDING_DEFAULT);

    /**
     * Method to tell whether unnamed busses should be numbered ascending.
     * The alternative is descending.
     * @return true if unnamed busses should be numbered ascending.
     */
    public static boolean isBusAscending() {
        return cacheBusAscending.getBoolean();
    }

    /**
     * Method to set whether unnamed busses should be numbered ascending.
     * The alternative is descending.
     * @param a true if unnamed busses should be numbered ascending.
     */
    public static void setBusAscending(boolean a) {
        cacheBusAscending.setBoolean(a);
    }

    /**
     * Method to tell whether unnamed busses should be numbered ascending, by default.
     * The alternative is descending.
     * @return true if unnamed busses should be numbered ascending, by default.
     */
    public static boolean isFactoryBusAscending() {
        return cacheBusAscending.getBooleanFactoryValue();
    }

    /**
     * Method to tell whether unnamed busses should be numbered ascending in Netlist Engine.
     * The alternative is descending.
     * The method always returns false now.
     * It can be same as isBusAscending() after Netlist Engine correctly renumbers networks
     * after change this preference.
     * @return true if unnamed busses should be numbered ascending.
     */
    public static boolean isBusAscendingInNetlistEngine() {
//        return cacheBusAscending.getBoolean();
        return BUS_ASCENDING_DEFAULT;
    }
}
