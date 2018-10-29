/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: DEF.java
 * Input/output tool: DEF output
 * Written by Steven M. Rubin.
 *
 * Copyright (c) 2014, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.io.output;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.ERectangle;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortOriginal;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.text.Version;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.util.math.FixpRectangle;
import com.sun.electric.util.math.FixpTransform;
import com.sun.electric.util.math.Orientation;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This class writes files in DEF format.
 */
public class DEF extends Output
{
	private DEFPreferences localPrefs;

	public static class DEFPreferences extends OutputPreferences
	{
		private DEF out;
		private double scaleFactor;
		private Technology tech;

		public DEFPreferences(boolean factory)
		{
			super(factory);
		}

		@Override
        public Output doOutput(Cell cell, VarContext context, String filePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Output doOutput(Cell cell, VarContext context, String filePath, EditingPreferences ep)
		{
			out = new DEF(this);
			tech = cell.getTechnology();
			if (out.openTextOutputStream(filePath)) return out.finishWrite();

			// write the header
			out.printWriter.println("VERSION 5.6 ;");
			out.printWriter.println("DIVIDERCHAR \"/\" ;");
			out.printWriter.println("BUSBITCHARS \"[]\" ;");
			out.printWriter.println();
			out.printWriter.println("DESIGN " + cell.getName() + " ;");

			// write header Units
			double nanometersPerUnit = cell.getTechnology().getScale();
			scaleFactor = nanometersPerUnit*100;
			out.printWriter.println();
			out.printWriter.println("UNITS DISTANCE MICRONS " + TextUtils.formatDouble(scaleFactor) + " ;");

			// write header comments
			if (includeDateAndVersionInOutput)
			{
				Date now = new Date();
				out.printWriter.println();
				out.printWriter.println("HISTORY written by the Electric VLSI Design System, version " + Version.getVersion() +
					" on " + TextUtils.formatDate(now) + " ;");
			}
			if (IOTool.isUseCopyrightMessage())
			{
				// should use out.emitCopyright("HISTORY ", " ;"); but it doesn't convert semicolons
				String str = IOTool.getCopyrightMessage();
				int start = 0;
				while (start < str.length())
				{
					int endPos = str.indexOf('\n', start);
					if (endPos < 0) endPos = str.length();
					String oneLine = str.substring(start, endPos).replaceAll(";", ",");
					out.printWriter.println("HISTORY " + oneLine + " ;");
					start = endPos+1;
				}
			}

			// write header die area (bounding box)
			ERectangle cellBounds = cell.getBounds();
			out.printWriter.println();
			out.printWriter.println("DIEAREA ( " + convertToDEF(cellBounds.getMinX()) + " " + convertToDEF(cellBounds.getMinY()) + " ) ( " +
				convertToDEF(cellBounds.getMaxX()) + " " + convertToDEF(cellBounds.getMaxY()) + " ) ;");

			// write all vias
			Map<NodeInst,String> contactNodes = new HashMap<NodeInst,String>();
			Map<String,NodeInst> contactExamples = new TreeMap<String,NodeInst>();
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance()) continue;
				PrimitiveNode np = (PrimitiveNode)ni.getProto();
				PrimitiveNode.Function fun = np.getFunction();
				if (fun.isPin()) continue;
				if (fun.isContact() || fun == PrimitiveNode.Function.CONNECT ||
					fun == PrimitiveNode.Function.SUBSTRATE || fun == PrimitiveNode.Function.WELL)
				{
					String cName = np.getName();
					if (np.getTechnology() != tech) cName += "_TECH-" + np.getTechnology().getTechName();
					if (ni.getXSize() != np.getDefWidth(ep) || ni.getYSize() != np.getDefHeight(ep))
						cName += "_SIZE-" + TextUtils.formatDouble(ni.getXSize()) + "x" + TextUtils.formatDouble(ni.getYSize());
					if (ni.getOrient() != Orientation.IDENT)
						cName += "_ORIENT-" + ni.getOrient().toString();
					contactNodes.put(ni, cName);
					contactExamples.put(cName, ni);
				}
			}
			if (contactExamples.size() > 0)
			{
				out.printWriter.println();
				out.printWriter.println("VIAS " + contactExamples.size() + " ;");
				for(String cName : contactExamples.keySet())
				{
					out.printWriter.println("- " + cName);
					NodeInst ni = contactExamples.get(cName);
					Poly[] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
					FixpTransform trans = ni.rotateOut();
					for(Poly poly : polys)
					{
						poly.transform(trans);
						Layer layer = poly.getLayer();
						String layerName = getLayerName(layer);
						if (layerName == null)
						{
							System.out.println("ERROR: Cannot determine DEF layer for " + layer.getName() + " on node " + ni.describe(false));
							continue;
						}
						FixpRectangle bound = poly.getBounds2D();
						double lX = bound.getMinX() - ni.getAnchorCenterX();
						double hX = bound.getMaxX() - ni.getAnchorCenterX();
						double lY = bound.getMinY() - ni.getAnchorCenterY();
						double hY = bound.getMaxY() - ni.getAnchorCenterY();
						out.printWriter.println("  + RECT " + layerName + " ( " + convertToDEF(lX) + " " + convertToDEF(lY) + " ) ( " +
							convertToDEF(hX) + " " + convertToDEF(hY) + " )");
					}
					out.printWriter.println("  ;");
				}
				out.printWriter.println("END VIAS");
			}

			// write components (instances)
			List<NodeInst> cellInstances = new ArrayList<NodeInst>();
			for(Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
			{
				NodeInst ni = it.next();
				if (ni.isCellInstance()) cellInstances.add(ni);
			}
			if (cellInstances.size() > 0)
			{
				out.printWriter.println();
				out.printWriter.println("COMPONENTS " + cellInstances.size() + " ;");
				for(NodeInst ni : cellInstances)
				{
					String orientation = null;
					if (ni.getOrient() == Orientation.IDENT) orientation = "N";
					if (ni.getOrient() == Orientation.R) orientation = "W";
					if (ni.getOrient() == Orientation.RR) orientation = "S";
					if (ni.getOrient() == Orientation.RRR) orientation = "E";
					if (ni.getOrient() == Orientation.Y) orientation = "FS";
					if (ni.getOrient() == Orientation.YR) orientation = "FW";
					if (ni.getOrient() == Orientation.YRR) orientation = "FN";
					if (ni.getOrient() == Orientation.YRRR) orientation = "FE";
					if (ni.getOrient() == Orientation.X) orientation = "FN";
					if (ni.getOrient() == Orientation.XR) orientation = "FE";
					if (ni.getOrient() == Orientation.XRR) orientation = "FS";
					if (ni.getOrient() == Orientation.XRRR) orientation = "FW";
					if (ni.getOrient() == Orientation.XY) orientation = "S";
					if (ni.getOrient() == Orientation.XYR) orientation = "E";
					if (ni.getOrient() == Orientation.XYRR) orientation = "N";
					if (ni.getOrient() == Orientation.XYRRR) orientation = "W";
					if (orientation == null)
					{
						System.out.println("ERROR: Cannot handle nonmanhattan instance: " + ni.describe(false));
						continue;
					}
					double nx = ni.getAnchorCenterX(), ny = ni.getAnchorCenterY();
					double width = ni.getXSize();
					double height = ni.getYSize();
					if (orientation.equals("FN")) nx = nx - width;
					if (orientation.equals("FS")) ny = ny - height;
					if (orientation.equals("FW")) { nx = nx - height; ny = ny - width; }
					if (orientation.equals("S")) { ny = ny - height; nx = nx - width; }
					if (orientation.equals("E")) ny = ny - width;
					if (orientation.equals("W")) nx = nx - height;
					out.printWriter.println("- " + ni.getName() + " " + ni.getProto().getName() +
						" + PLACED ( " + convertToDEF(nx) + " " + convertToDEF(ny) + " ) " + orientation + " ;");
				}
				out.printWriter.println("END COMPONENTS");
			}

			// write pins (exports)
			Netlist nl = cell.getNetlist();
			if (cell.getNumPorts() > 0)
			{
				out.printWriter.println();
				out.printWriter.println("PINS " + cell.getNumPorts() + " ;");
				for(Iterator<Export> it = cell.getExports(); it.hasNext(); )
				{
					Export e = it.next();
					String direction = "INPUT";
					if (e.getCharacteristic() == PortCharacteristic.OUT) direction = "OUTPUT";
					if (e.getCharacteristic() == PortCharacteristic.BIDIR) direction = "INOUT";
					Network net = nl.getNetwork(e, 0);
					out.printWriter.print("- " + e.getName() + " + NET " + net.getName() + " + DIRECTION " + direction);
					if (e.getCharacteristic() == PortCharacteristic.GND) out.printWriter.print(" + USE GROUND");
					if (e.getCharacteristic() == PortCharacteristic.PWR) out.printWriter.print(" + USE POWER");
					if (e.getCharacteristic().isClock()) out.printWriter.print(" + USE CLOCK");
					out.printWriter.println();

					out.printWriter.print(" ");
		            PortOriginal fp = new PortOriginal(e.getOriginalPort());
					FixpTransform trans = fp.getTransformToTop();
					NodeInst ni = fp.getBottomNodeInst();
					PrimitiveNode pnp = (PrimitiveNode)ni.getProto();
					Poly[] polys = pnp.getTechnology().getShapeOfNode(ni);
					if (polys.length == 0)
					{
						// pin may have no geometry
						polys = new Poly[1];
						polys[0] = new Poly(ni.getAnchorCenterX(), ni.getAnchorCenterY(), ni.getXSize(), ni.getYSize());
						polys[0].setLayer(pnp.getLayerIterator().next());
					}
					for(int i=0; i<polys.length; i++)
					{
						Poly poly = polys[i];
						Layer layer = poly.getLayer();
						String layerName = null;
						if (layer.getFunction().isMetal()) layerName = layer.getName(); // "METAL" + layer.getFunction().getLevel();
						if (layer.getFunction().isPoly()) layerName = "POLY" + layer.getFunction().getLevel();
						if (layerName != null)
						{
							poly.transform(trans);
							FixpRectangle rect = poly.getBounds2D();
							out.printWriter.print(" + LAYER " + layerName + " ( " + convertToDEF(-rect.getWidth()/2) + " " + convertToDEF(-rect.getHeight()/2) + " ) ( "+
								convertToDEF(rect.getWidth()/2) + " " + convertToDEF(rect.getHeight()/2) + " )");
						}
					}

					EPoint ctr = e.getPoly().getCenter();
					out.printWriter.println(" + PLACED ( " + convertToDEF(ctr.getX()) + " " + convertToDEF(ctr.getY()) + " ) N ;");
				}
				out.printWriter.println("END PINS");
			}

			// build lists of nodes on the networks
			Map<Network,List<NodeInst>> nodesOnNets = new HashMap<Network,List<NodeInst>>();
	        for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext(); )
	        {
	            NodeInst ni = it.next();
	            for (Iterator<PortInst> pit = ni.getPortInsts(); pit.hasNext(); )
	            {
	                PortInst pi = pit.next();
	                Network net = nl.getNetwork(pi);
	                List<NodeInst> niList = nodesOnNets.get(net);
	                if (niList == null) nodesOnNets.put(net, niList = new ArrayList<NodeInst>());
	                niList.add(ni);
	                break;
	            }
	        }

	        // build lists of ports on the networks
	        Map<Network,List<PortInst>> portsOnNets = new TreeMap<Network,List<PortInst>>();
			for(Iterator<Network> it = nl.getNetworks(); it.hasNext(); )
			{
				Network net = it.next();
				// see if a single pure-layer node is on this net
				List<NodeInst> niList = nodesOnNets.get(net);
				if (niList != null && niList.size() == 1)
				{
					NodeInst ni = niList.get(0);
					if (ni.getProto().getFunction() == PrimitiveNode.Function.NODE)
					{
						List<PortInst> portList = new ArrayList<PortInst>(1);
						portList.add(ni.getOnlyPortInst());
						portsOnNets.put(net, portList);
						continue;
					}
				}

				portsOnNets.put(net, net.getPortsList());
			}

			// write the networks
			if (portsOnNets.size() > 0)
			{
				out.printWriter.println();
				out.printWriter.println("SPECIALNETS " + portsOnNets.size() + " ;");
				for(Network net : portsOnNets.keySet())
				{
					List<PortInst> branches = portsOnNets.get(net);
					String safeName = net.getName().replaceAll("@", "-");
					out.printWriter.print("- " + safeName);

					// write all exports on the net
					for(Iterator<Export> eIt = net.getExports(); eIt.hasNext(); )
					{
						Export e = eIt.next();
						out.printWriter.print(" ( PIN " + e.getName() + " )");
					}

					// write all cell connections on the net
					Set<PortInst> portsSeen = new HashSet<PortInst>();
					for(PortInst pi : branches)
					{
						if (pi.getNodeInst().isCellInstance() && !portsSeen.contains(pi))
						{
							portsSeen.add(pi);
							out.printWriter.print(" ( " + pi.getNodeInst().getName() + " " + pi.getPortProto().getName() + " )");
						}
					}
					out.printWriter.println();

					// now write the vias on the net
					List<NodeInst> niList = nodesOnNets.get(net);
					if (niList != null)
					{
						for(NodeInst ni : niList)
						{
							if (ni.isCellInstance()) continue;
							PrimitiveNode.Function fun = ni.getFunction();
							String name = contactNodes.get(ni);
							if (name != null)
							{
								out.printWriter.println("  + FIXED M1 0 ( " + convertToDEF(ni.getAnchorCenterX()) + " " + convertToDEF(ni.getAnchorCenterY()) + " ) " + name);
							} else
							{
								if (fun.isPin()) continue;
								Poly[] polys = ni.getProto().getTechnology().getShapeOfNode(ni);
								FixpTransform trans = ni.rotateOut();
								for(Poly poly : polys)
								{
									if (poly.getLayer().getTechnology() == Generic.tech()) continue;
									poly.transform(trans);
									String layerName = getLayerName(poly.getLayer());
									if (layerName == null)
									{
										System.out.println("ERROR: Cannot determine DEF layer for " + poly.getLayer().getName() + " on node " + ni.describe(false));
										continue;
									}
									FixpRectangle bound = poly.getBounds2D();
									double x1, y1, x2, y2, wid;
									if (bound.getWidth() > bound.getHeight())
									{
										x1 = bound.getMinX();   y1 = bound.getCenterY();
										x2 = bound.getMaxX();   y2 = bound.getCenterY();
										wid = bound.getHeight();
									} else
									{
										x1 = bound.getCenterX();   y1 = bound.getMinY();
										x2 = bound.getCenterX();   y2 = bound.getMaxY();
										wid = bound.getWidth();
									}

									// make sure the width is even (Virtuoso insists)
									long evenWid = Math.round(TextUtils.convertDistance(wid * scaleFactor, tech, TextUtils.UnitScale.MICRO));
									if ((evenWid%2) != 0) evenWid--;
									long x1Int = Math.round(TextUtils.convertDistance(x1 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
									long y1Int = Math.round(TextUtils.convertDistance(y1 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
									long x2Int = Math.round(TextUtils.convertDistance(x2 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
									long y2Int = Math.round(TextUtils.convertDistance(y2 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
									if (x1Int == x2Int && y1Int == y2Int) continue;
									out.printWriter.println("  + FIXED " + layerName + " " + evenWid + " ( " + x1Int + " " +
										y1Int + " ) ( " + x2Int + " " + y2Int + " )");
								}
							}
						}
					}
					for(Iterator<ArcInst> it = net.getArcs(); it.hasNext(); )
					{
						ArcInst ai = it.next();
						Poly[] polys = ai.getProto().getTechnology().getShapeOfArc(ai);
						for(Poly poly : polys)
						{
							if (poly.getLayer().getTechnology() == Generic.tech()) continue;
							String layerName = getLayerName(poly.getLayer());
							if (layerName == null)
							{
								System.out.println("ERROR: Cannot determine DEF layer for " + poly.getLayer().getName() + " on arc " + ai.describe(false));
								continue;
							}
							FixpRectangle bound = poly.getBounds2D();
							double x1, y1, x2, y2, wid;
							if (bound.getWidth() > bound.getHeight())
							{
								x1 = bound.getMinX();   y1 = bound.getCenterY();
								x2 = bound.getMaxX();   y2 = bound.getCenterY();
								wid = bound.getHeight();
							} else
							{
								x1 = bound.getCenterX();   y1 = bound.getMinY();
								x2 = bound.getCenterX();   y2 = bound.getMaxY();
								wid = bound.getWidth();
							}

							// make sure the width is even (Virtuoso insists)
							long evenWid = Math.round(TextUtils.convertDistance(wid * scaleFactor, tech, TextUtils.UnitScale.MICRO));
							if ((evenWid%2) != 0) evenWid--;
							long x1Int = Math.round(TextUtils.convertDistance(x1 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
							long y1Int = Math.round(TextUtils.convertDistance(y1 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
							long x2Int = Math.round(TextUtils.convertDistance(x2 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
							long y2Int = Math.round(TextUtils.convertDistance(y2 * scaleFactor, tech, TextUtils.UnitScale.MICRO));
							if (x1Int == x2Int && y1Int == y2Int) continue;
							out.printWriter.println("  + FIXED " + layerName + " " + evenWid + " ( " + x1Int + " " +
								y1Int + " ) ( " + x2Int + " " + y2Int + " )");
						}
					}

					out.printWriter.println(" ;");
				}
				out.printWriter.println("END SPECIALNETS");
			}

			// all done
			out.printWriter.println();
			out.printWriter.println("END DESIGN");

			if (out.closeTextOutputStream()) return out.finishWrite();
			System.out.println(filePath + " written");
			return out.finishWrite();
		}

        private String getLayerName(Layer layer)
        {
			String layerName = null;
			Layer.Function fun = layer.getFunction();
			if (fun.isMetal()) layerName = layer.getName(); // "METAL" + fun.getLevel();
			if (fun.isContact()) layerName = "VIA" + fun.getLevel();
			if (fun.isPoly())
			{
				layerName = "POLY";
				if (fun.getLevel() > 0) layerName += fun.getLevel();
			}
			if (fun.isDiff()) layerName = "DIFF";
			if (fun == Layer.Function.WELL) layerName = "WELL";
			if (fun == Layer.Function.WELLN) layerName = "NWEL";
			if (fun == Layer.Function.WELLP) layerName = "PWEL";
			if (fun == Layer.Function.SUBSTRATE) layerName = "SUB";
			if (fun == Layer.Function.IMPLANT) layerName = "SUB";
			if (fun == Layer.Function.IMPLANTN) layerName = "NP";
			if (fun == Layer.Function.IMPLANTP) layerName = "PP";
			return layerName;
        }

        private String convertToDEF(double v)
		{
			v *= scaleFactor;
			return TextUtils.formatDouble(TextUtils.convertDistance(v, tech, TextUtils.UnitScale.MICRO));
		}
	}

	/** Creates a new instance of DEF */
	private DEF(DEFPreferences hp) { localPrefs = hp; }
}
