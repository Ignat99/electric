/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: ToolMenu.java
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

package com.sun.electric.tool.user.menus;

import static com.sun.electric.tool.user.menus.EMenuItem.SEPARATOR;
import static com.sun.electric.util.collections.ArrayIterator.i2i;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.ImmutableExport;
import com.sun.electric.database.geometry.EPoint;
import com.sun.electric.database.geometry.GeometryHandler;
import com.sun.electric.database.geometry.Poly;
import com.sun.electric.database.geometry.btree.EquivalenceClasses;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.EDatabase;
import com.sun.electric.database.hierarchy.Export;
import com.sun.electric.database.hierarchy.HierarchyEnumerator;
import com.sun.electric.database.hierarchy.HierarchyEnumerator.CellInfo;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.Nodable;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.database.id.CellUsage;
import com.sun.electric.database.network.Netlist;
import com.sun.electric.database.network.Network;
import com.sun.electric.database.prototype.NodeProto;
import com.sun.electric.database.prototype.PortCharacteristic;
import com.sun.electric.database.prototype.PortProto;
import com.sun.electric.database.text.TextUtils;
import com.sun.electric.database.topology.ArcInst;
import com.sun.electric.database.topology.Connection;
import com.sun.electric.database.topology.NodeInst;
import com.sun.electric.database.topology.PortInst;
import com.sun.electric.database.variable.DisplayedText;
import com.sun.electric.database.variable.EditWindow_;
import com.sun.electric.database.variable.ElectricObject;
import com.sun.electric.database.variable.TextDescriptor;
import com.sun.electric.database.variable.UserInterface;
import com.sun.electric.database.variable.VarContext;
import com.sun.electric.database.variable.Variable;
import com.sun.electric.lib.LibFile;
import com.sun.electric.technology.DRCTemplate;
import com.sun.electric.technology.Foundry;
import com.sun.electric.technology.Layer;
import com.sun.electric.technology.PrimitiveNode;
import com.sun.electric.technology.PrimitivePort;
import com.sun.electric.technology.Technology;
import com.sun.electric.technology.technologies.Generic;
import com.sun.electric.technology.technologies.Schematics;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.JobException;
import com.sun.electric.tool.Tool;
import com.sun.electric.tool.compaction.Compaction;
import com.sun.electric.tool.drc.AssuraDrcErrors;
import com.sun.electric.tool.drc.CalibreDrcErrors;
import com.sun.electric.tool.drc.DRC;
import com.sun.electric.tool.drc.MTDRCLayoutTool;
import com.sun.electric.tool.erc.ERCAntenna;
import com.sun.electric.tool.erc.ERCWellCheck;
import com.sun.electric.tool.extract.Connectivity;
import com.sun.electric.tool.extract.LayerCoverageTool;
import com.sun.electric.tool.extract.ParasiticTool;
import com.sun.electric.tool.generator.AcuteAngleFill;
import com.sun.electric.tool.generator.PadGenerator;
import com.sun.electric.tool.generator.ROMGenerator;
import com.sun.electric.tool.generator.cmosPLA.PLA;
import com.sun.electric.tool.generator.layout.GateLayoutGenerator;
import com.sun.electric.tool.generator.layout.fill.StitchFillJob;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.IOTool;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.io.input.SimulationData;
import com.sun.electric.tool.io.input.verilog.VerilogReader;
import com.sun.electric.tool.io.output.GenerateVHDL;
import com.sun.electric.tool.io.output.Spice;
import com.sun.electric.tool.io.output.Verilog;
import com.sun.electric.tool.lang.EvalJavaBsh;
import com.sun.electric.tool.lang.EvalJython;
import com.sun.electric.tool.logicaleffort.LENetlister;
import com.sun.electric.tool.logicaleffort.LETool;
import com.sun.electric.tool.ncc.AllSchemNamesToLay;
import com.sun.electric.tool.ncc.CalibreLVSErrors;
import com.sun.electric.tool.ncc.Ncc;
import com.sun.electric.tool.ncc.NccCrossProbing;
import com.sun.electric.tool.ncc.NccJob;
import com.sun.electric.tool.ncc.NccOptions;
import com.sun.electric.tool.ncc.PassedNcc;
import com.sun.electric.tool.ncc.SchemNamesToLay;
import com.sun.electric.tool.ncc.basic.NccCellAnnotations;
import com.sun.electric.tool.ncc.basic.NccUtils;
import com.sun.electric.tool.ncc.netlist.NccNetlist;
import com.sun.electric.tool.ncc.result.NccResult;
import com.sun.electric.tool.ncc.result.NccResults;
import com.sun.electric.tool.ncc.result.equivalence.Equivalence;
import com.sun.electric.tool.placement.Placement;
import com.sun.electric.tool.routing.AutoStitch;
import com.sun.electric.tool.routing.ClockRouter;
import com.sun.electric.tool.routing.Maze;
import com.sun.electric.tool.routing.MimicStitch;
import com.sun.electric.tool.routing.River;
import com.sun.electric.tool.routing.Routing;
import com.sun.electric.tool.routing.RoutingFrame;
import com.sun.electric.tool.routing.SeaOfGates;
import com.sun.electric.tool.routing.metrics.WireQualityMetric;
import com.sun.electric.tool.sc.GetNetlist;
import com.sun.electric.tool.sc.Maker;
import com.sun.electric.tool.sc.Place;
import com.sun.electric.tool.sc.Route;
import com.sun.electric.tool.sc.SilComp;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.simulation.irsim.IRSIM;
import com.sun.electric.tool.user.CompileVHDL;
import com.sun.electric.tool.user.CompileVerilogStruct;
import com.sun.electric.tool.user.ErrorLogger;
import com.sun.electric.tool.user.Highlight;
import com.sun.electric.tool.user.Highlighter;
import com.sun.electric.tool.user.IconParameters;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.UserInterfaceMain;
import com.sun.electric.tool.user.dialogs.CellBrowser;
import com.sun.electric.tool.user.dialogs.FastHenryArc;
import com.sun.electric.tool.user.dialogs.FillGenDialog;
import com.sun.electric.tool.user.dialogs.LanguageScripts;
import com.sun.electric.tool.user.dialogs.MultiFingerTransistor;
import com.sun.electric.tool.user.dialogs.OpenFile;
import com.sun.electric.tool.user.dialogs.SeaOfGatesCell;
import com.sun.electric.tool.user.ncc.HighlightEquivalent;
import com.sun.electric.tool.user.ui.EditWindow;
import com.sun.electric.tool.user.ui.TextWindow;
import com.sun.electric.tool.user.ui.TopLevel;
import com.sun.electric.tool.user.ui.WindowFrame;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;


/**
 * Class to handle the commands in the "Tools" pulldown menu.
 */
public class ToolMenu {
	private static EMenu languageMenu = null, expRoutingMenu = null;

	static EMenu makeMenu() {
		/****************************** THE TOOLS MENU ******************************/

		// mnemonic keys available: A CDEFGHI KLMNOPQR TUVWXYZ
		languageMenu = new EMenu("Lang_uages",
			new EMenuItem("Run Java _Bean Shell Script...") {
				public void run() { javaBshScriptCommand(); }
			},
			EvalJython.hasJython() ? new EMenuItem("Run _Jython Script...") {
				public void run() { jythonScriptCommand(); }
			} : null,
			new EMenuItem("Manage _Scripts...") {
				public void run() { new LanguageScripts(); }
			},
			SEPARATOR);

		SwingUtilities.invokeLater(new Runnable() {
			public void run() { setDynamicLanguageMenu(); }
		});

		RoutingFrame[] expRouters = RoutingFrame.getRoutingAlgorithms();
		expRoutingMenu = new EMenu("Experimental Routers",
				expRouters.length > 0 ? new DynamicExperimentalRoutingMenuItem(expRouters[0]) : null,
				expRouters.length > 1 ? new DynamicExperimentalRoutingMenuItem(expRouters[1]) : null,
				expRouters.length > 2 ? new DynamicExperimentalRoutingMenuItem(expRouters[2]) : null,
				expRouters.length > 3 ? new DynamicExperimentalRoutingMenuItem(expRouters[3]) : null,
				expRouters.length > 4 ? new DynamicExperimentalRoutingMenuItem(expRouters[4]) : null,
				expRouters.length > 5 ? new DynamicExperimentalRoutingMenuItem(expRouters[5]) : null,
				expRouters.length > 6 ? new DynamicExperimentalRoutingMenuItem(expRouters[6]) : null,
				expRouters.length > 7 ? new DynamicExperimentalRoutingMenuItem(expRouters[7]) : null);

		// calculating boolean only once
		boolean hasIRSIM = IRSIM.hasIRSIM();
		// mnemonic keys available: B F H JK Q XYZ
		return new EMenu("_Tools",

			// ------------------- DRC
			// mnemonic keys available:  B   FG  J   NOPQR  UVW YZ
			new EMenu("_DRC",
				new EMenuItem("Check _Hierarchically", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0)) {
					public void run() {
						DRC.DRCPreferences dp = new DRC.DRCPreferences(false);
						Cell cell = Job.getUserInterface().needCurrentCell();
						// Multi-threaded code is only available for layout
						if (dp.isMultiThreaded && cell.isLayout()) {
							new MTDRCLayoutTool(dp, cell, true, null).startJob();
						} else {
							DRC.checkDRCHierarchically(dp, cell, null, null, GeometryHandler.GHMode.ALGO_SWEEP, false);
						}
					}
				},
				new EMenuItem("Check _Selection Hierarchically") {
					public void run() {
						EditWindow_ wnd = Job.getUserInterface().getCurrentEditWindow_();
						if (wnd == null) return;
						DRC.DRCPreferences dp = new DRC.DRCPreferences(false);
						DRC.checkDRCHierarchically(dp, wnd.getCell(), wnd.getHighlightedEObjs(true, true),
								wnd.getHighlightedArea(), GeometryHandler.GHMode.ALGO_SWEEP, false);
					}
				},
				new EMenuItem("Add S_kip Annotation to Cell") {
					public void run() { DRC.makeDRCAnnotation(); }
				},
				new EMenuItem("Check Area _Coverage") {
					public void run() {
						LayerCoverageTool.layerCoverageCommand(WindowFrame.needCurCell(), GeometryHandler.GHMode.ALGO_SWEEP,
							true, new LayerCoverageTool.LayerCoveragePreferences(false));
					}
				},
				new EMenuItem("_List Layer Coverage on Cell") {
					public void run() { layerCoverageCommand(LayerCoverageTool.LCMode.AREA, GeometryHandler.GHMode.ALGO_SWEEP); }
				},

				SEPARATOR,

				new EMenuItem("Import _Assura DRC Errors for Current Cell...") {
					public void run() { importAssuraDrcErrors(); }
				},
				new EMenuItem("Import Calibre _DRC Errors for Current Cell...") {
					public void run() { importCalibreDrcErrors(); }
				},

				SEPARATOR,

				new EMenuItem("_Export DRC Deck...") {
					public void run() { exportDRCDeck(); }
				},
				new EMenuItem("_Import DRC Deck...") {
					public void run() { importDRCDeck(); }
                }),

			// ------------------- Simulation (Built-in)
			// mnemonic keys available: B JK N PQ XYZ
			new EMenu("Simulation (Built-in)",
				hasIRSIM ? new EMenuItem("IRSI_M: Simulate Current Cell") {
					public void run() { SimulationTool.startSimulation(SimulationTool.IRSIM_ENGINE, null, null, null, false); }
				} : null,
				hasIRSIM ? new EMenuItem("IRSIM: _Write Deck...") {
					public void run() { FileMenu.exportCommand(FileType.IRSIM, true); }
				} : null,
				hasIRSIM ? new EMenuItem("_IRSIM: Simulate Deck...") {
					public void run() { SimulationTool.startSimulation(SimulationTool.IRSIM_ENGINE, "", null, null, false); }
				} : null,

				hasIRSIM ? SEPARATOR : null,

				new EMenuItem("_ALS: Simulate Current Cell") {
					public void run() { SimulationTool.startSimulation(SimulationTool.ALS_ENGINE, null, null, null, false); }
				},

				SEPARATOR,

				new EMenuItem("Set Signal _High at Main Time", KeyStroke.getKeyStroke('V', 0)) {
					public void run() { SimulationTool.setSignalHigh(); }
				},
				new EMenuItem("Set Signal _Low at Main Time", KeyStroke.getKeyStroke('G', 0)) {
					public void run() { SimulationTool.setSignalLow(); }
				},
				new EMenuItem("Set Signal Un_defined at Main Time", KeyStroke.getKeyStroke('X', 0)) {
					public void run() { SimulationTool.setSignalX(); }
				},
				new EMenuItem("Set Clock on Selected Signal...") {
					public void run() { SimulationTool.setClock(); }
				},

				SEPARATOR,

				new EMenuItem("_Update Simulation Window") {
					public void run() { SimulationTool.update(); }
				},
				new EMenuItem("_Get Information about Selected Signals") {
					public void run() { SimulationTool.showSignalInfo(); }
				},

				SEPARATOR,

				new EMenuItem("_Clear Selected Stimuli") {
					public void run() { SimulationTool.removeSelectedStimuli(); }
				},
				new EMenuItem("Clear All Stimuli _on Selected Signals") {
					public void run() { SimulationTool.removeStimuliFromSignal(); }
				},
				new EMenuItem("Clear All S_timuli") {
					public void run() { SimulationTool.removeAllStimuli(); }
				},

				SEPARATOR,

				new EMenuItem("_Save Stimuli to Disk...") {
					public void run() { SimulationTool.saveStimuli(); }
				},
				new EMenuItem("_Restore Stimuli from Disk...") {
					public void run() { SimulationTool.restoreStimuli(null); }
				}),

			// ------------------- Simulation (SPICE)
			// mnemonic keys available: B  JK QR VWXYZ
			new EMenu("Simulation (_Spice)",
				new EMenuItem("Write Spice _Deck...") {
					public void run() { FileMenu.exportCommand(FileType.SPICE, true); }
				},
				new EMenuItem("Write _CDL Deck...") {
					public void run() { FileMenu.exportCommand(FileType.CDL, true); }
				},
				new EMenuItem("Plot Simulation _Output, Choose File...") {
					public void run() { plotChosen(); }
				},
				new EMenuItem("Plot Simulation Output, _Guess File") {
					public void run() {
						UserInterface ui = Job.getUserInterface();
						Cell cell = ui.needCurrentCell();
						if (cell == null) return;
						SimulationData.plotGuessed(cell, null);
					}
				},
				new EMenuItem("Set Spice _Model...") {
					public void run() { SimulationTool.setSpiceModel(); }
				},
				new EMenuItem("Add M_ultiplier") {
					public void run() { addMultiplierCommand(); }
				},
				new EMenuItem("Add Flat Cod_e") {
					public void run() { makeTemplate(Spice.SPICE_CODE_FLAT_KEY, false); }
				},

				SEPARATOR,

				new EMenuItem("Set Generic Spice _Template") {
					public void run() { makeTemplate(Spice.SPICE_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set Spice _2 Template") {
					public void run() { makeTemplate(Spice.SPICE_2_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set Spice _3 Template") {
					public void run() { makeTemplate(Spice.SPICE_3_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set Ngsp_ice Template") {
					public void run() { makeTemplate(Spice.SPICE_NG_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set _HSpice Template") {
					public void run() { makeTemplate(Spice.SPICE_H_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set _PSpice Template") {
					public void run() { makeTemplate(Spice.SPICE_P_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set G_nuCap Template") {
					public void run() { makeTemplate(Spice.SPICE_GC_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set _SmartSpice Template") {
					public void run() { makeTemplate(Spice.SPICE_SM_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set _Assura HSpice Template") {
					public void run() { makeTemplate(Spice.SPICE_A_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set Ca_libre Spice Template") {
					public void run() { makeTemplate(Spice.SPICE_C_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set Netlist Cell From _File") {
					public void run() { makeTemplate(Spice.SPICE_NETLIST_FILE_KEY, true); }
				},
				new EMenuItem("Set CDL Template") {
					public void run() { makeTemplate(Spice.CDL_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set _Xyce Template") {
					public void run() { makeTemplate(Spice.SPICE_XYCE_TEMPLATE_KEY, false); }
				}
				),

			// ------------------- Simulation (Verilog)
			// mnemonic keys available: BCDEF HIJKLMN QR U XYZ
			new EMenu("Simulation (_Verilog)",
				new EMenuItem("Write _Verilog Deck...") {
					public void run() { FileMenu.exportCommand(FileType.VERILOG, true); }
				},
				new EMenuItem("Write Verilog_A Deck...") {
					public void run() {
						SimulationTool.setVerilogStopAtStandardCells(false);
						FileMenu.exportCommand(FileType.VERILOGA, true);
					}
				},
				new EMenuItem("Plot Simulation _Output, Choose File...") {
					public void run() { plotChosen(); }
				},
				new EMenuItem("Plot Simulation Output, _Guess File") {
					public void run() {
						UserInterface ui = Job.getUserInterface();
						Cell cell = ui.needCurrentCell();
						if (cell == null) return;
						SimulationData.plotGuessed(cell, null);
					}
				},

				SEPARATOR,

				new EMenuItem("Set Verilog _Template") {
					public void run() { makeTemplate(Verilog.VERILOG_TEMPLATE_KEY, false); }
				},
				new EMenuItem("Set Verilog Default _Parameter") {
					public void run() { makeTemplate(Verilog.VERILOG_DEFPARAM_KEY, false); }
				},

				SEPARATOR,

				// mnemonic keys available: ABC EFGHIJKLMNOPQRS UV XYZ
				new EMenu("Set Verilog _Wire", new EMenuItem("_Wire") {
					public void run() { SimulationTool.setVerilogWireCommand(0); }
				},
				new EMenuItem("_Trireg") {
					public void run() { SimulationTool.setVerilogWireCommand(1); }
				},
				new EMenuItem("_Default") {
					public void run() { SimulationTool.setVerilogWireCommand(2); }
				}),

				// mnemonic keys available: ABCDEFGHIJKLM OPQRSTUV XYZ
				new EMenu("Transistor _Strength", new EMenuItem("_Weak") {
					public void run() { SimulationTool.setTransistorStrengthCommand(true); }
				},
				new EMenuItem("_Normal") {
					public void run() { SimulationTool.setTransistorStrengthCommand(false); }
				})),

			// ------------------- Simulation (others)
			// mnemonic keys available: B   G KL N Q UVWXYZ
			new EMenu("Simulation (_Others)",
				new EMenuItem("Write _Maxwell Deck...") {
					public void run() { FileMenu.exportCommand(FileType.MAXWELL, true); }
				},
				new EMenuItem("Write _Tegas Deck...") {
					public void run() { FileMenu.exportCommand(FileType.TEGAS, true); }
				},
				new EMenuItem("Write _SILOS Deck...") {
					public void run() { FileMenu.exportCommand(FileType.SILOS, true); }
				},
				new EMenuItem("Write _PAL Deck...") {
					public void run() { FileMenu.exportCommand(FileType.PAL, true); }
				},
				new EMenuItem("Write _DualEval Deck...") {
					public void run() { FileMenu.exportCommand(FileType.LISP, true); }
				},

				SEPARATOR,

				!hasIRSIM ? new EMenuItem("Write _IRSIM Deck...") {
					public void run() { FileMenu.exportCommand(FileType.IRSIM, true); }
				} : null,
				new EMenuItem("Write _ESIM/RNL Deck...") {
					public void run() { FileMenu.exportCommand(FileType.ESIM, true); }
				},
				new EMenuItem("Write _RSIM Deck...") {
					public void run() { FileMenu.exportCommand(FileType.RSIM, true); }
				},
				new EMenuItem("Write _COSMOS Deck...") {
					public void run() { FileMenu.exportCommand(FileType.COSMOS, true); }
				},
				new EMenuItem("Write M_OSSIM Deck...") {
					public void run() { FileMenu.exportCommand(FileType.MOSSIM, true); }
				},

				SEPARATOR,

				new EMenuItem("Write _FastHenry Deck...") {
					public void run() { FileMenu.exportCommand(FileType.FASTHENRY, true); }
				},
				new EMenuItem("Fast_Henry Arc Properties...") {
					public void run() { FastHenryArc.showFastHenryArcDialog(); }
				}),

			// ------------------- ERC
			// mnemonic keys available: BCDEFGHIJKLMNOPQRSTUV XYZ
			new EMenu("_ERC",
				new EMenuItem("Check _Wells") {
					public void run() { ERCWellCheck.analyzeCurCell(); }
				},
				new EMenuItem("_Antenna Check") {
					public void run() { ERCAntenna.doAntennaCheck(); }
				}),

			// ------------------- NCC
			// mnemonic keys available: B D FGHIJKLM O QRS VWXYZ
			new EMenu("_NCC",
				new EMenuItem("Schematic and Layout Views of Cell in _Current Window") {
					public void run() { new NccJob(1); }
				},
				new EMenuItem("Cells from _Two Windows") {
					public void run() { new NccJob(2); }
				},

				SEPARATOR,

				new EMenuItem("Copy Schematic _User Names to Layout") {
					public void run() { new SchemNamesToLay.RenameJob(); }
				},
				new EMenuItem("Copy All Schematic _Names to Layout") {
					public void run() { new AllSchemNamesToLay.RenameJob(); }
				},
				new EMenuItem("Highlight _Equivalent") {
					public void run() { HighlightEquivalent.highlight(); }
				},
				new EMenuItem("Run NCC for Schematic Cross-_Probing") {
					public void run() { runNccSchematicCrossProbing(); }
				},

				// mnemonic keys available: A CDE H KLM O Q U XYZ
				new EMenu("Add NCC _Annotation to Cell",
					new EMenuItem("Exports Connected by Parent _vdd") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("exportsConnectedByParent vdd /vdd_[0-9]+/"); }
					},
					new EMenuItem("Exports Connected By Parent _gnd") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("exportsConnectedByParent gnd /gnd_[0-9]+/"); }
					},
					new EMenuItem("Exports To _Ignore") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("exportsToIgnore /E_[0-9]+/"); }
					},
					new EMenuItem("_Skip NCC") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("skipNCC <comment explaining why>"); }
					},
					new EMenuItem("_Not a Subcircuit") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("notSubcircuit <comment explaining why>"); }
					},
					new EMenuItem("_Flatten Instances") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("flattenInstances <list of instance names>"); }
					},
					new EMenuItem("_Join Group") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("joinGroup <cell name>"); }
					},
					new EMenuItem("_Transistor Type") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("transistorType <typeName>"); }
					},
					new EMenuItem("_Resistor Type") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("resistorType <typeName>"); }
					},
					new EMenuItem("Force _Part Match") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("forcePartMatch <Part name shared by schematic and layout>"); }
					},
					new EMenuItem("Force _Wire Match") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("forceWireMatch <Wire name shared by schematic and layout>"); }
					},
					new EMenuItem("_Black Box") {
						public void run() { NccCellAnnotations.makeNCCAnnotationMenuCommand("blackBox <comment explaining why>"); }
					}),

					SEPARATOR,

					new EMenuItem("Import Calibre LVS Errors for Current Cell...") {
						public void run() { importCalibreLVSErrors(); }
					}),

			// ------------------- Network
			// mnemonic keys available: D F IJK M O S U W YZ
			new EMenu("Net_work",
				new EMenuItem("Show _Network", 'K') {
					public void run() { showNetworkCommand(); }
				},
				new EMenuItem("_List Networks") {
					public void run() { listNetworksCommand(); }
				},
				new EMenuItem("List _Connections on Network") {
					public void run() { listConnectionsOnNetworkCommand(); }
				},
				new EMenuItem("List _Exports on Network") {
					public void run() { listExportsOnNetworkCommand(); }
				},
				new EMenuItem("List Exports _below Network") {
					public void run() { listExportsBelowNetworkCommand(); }
				},
				new EMenuItem("List _Geometry on Network") {
					public void run() { listGeometryOnNetworkCommand(GeometryHandler.GHMode.ALGO_SWEEP); }
				},
				new EMenuItem("Calculate Network _Quality") {
					public void run() { calculateNetworkQualityCommand(); }
				},

				SEPARATOR,

				new EMenuItem("Show _All Networks") {
					public void run() { showAllNetworksCommand(); }
				},
				new EMenuItem("List _Total Wire Lengths on All Networks") {
					public void run() { listGeomsAllNetworksCommand(); }
				},
				new EMenuItem("Show Undriven Networks") {
					public void run() { showUndrivenNetworks(); }
				},

				SEPARATOR,

				new EMenuItem("E_xtract Current Cell") {
					public void run() { Connectivity.extractCurCell(false); }
				},
				new EMenuItem("Extract Current _Hierarchy") {
					public void run() { Connectivity.extractCurCell(true); }
				},

				SEPARATOR,

				new EMenuItem("Show _Power and Ground") {
					public void run() { showPowerAndGround(); }
				},
				new EMenuItem("_Validate Power and Ground") {
					public void run() { validatePowerAndGround(false); }
				},
				new EMenuItem("_Repair Power and Ground") {
					public void run() { new RepairPowerAndGround(); }
				}),

			// ------------------- Logical Effort
			// mnemonic keys available: D FGH JK M PQRSTUVWXYZ
			new EMenu("_Logical Effort",
				new EMenuItem("_Optimize for Equal Gate Delays") {
					public void run() { optimizeEqualGateDelaysCommand(true); }
				},
				new EMenuItem("Optimize for Equal Gate Delays (no _caching)") {
					public void run() { optimizeEqualGateDelaysCommand(false); }
				},
				new EMenuItem("List _Info for Selected Node") {
					public void run() { printLEInfoCommand(); }
				},
				new EMenuItem("_Back Annotate Wire Lengths for Current Cell") {
					public void run() { backAnnotateCommand(); }
				},
				new EMenuItem("Clear Sizes on Selected _Node(s)") {
					public void run() { clearSizesNodableCommand(); }
				},
				new EMenuItem("Clear Sizes in _all Libraries") {
					public void run() { clearSizesCommand(); }
				},
				new EMenuItem("_Estimate Delays") {
					public void run() { estimateDelaysCommand(); }
				},
				new EMenuItem("Add LE Attribute to Selected Export") {
					public void run() { addLEAttribute(); }
				},

				SEPARATOR,

				// Loading typical Logical Effort 180 libraries distributed with Electric
				makeSubMenusFromResources("_Load Logical Effort Libraries (Purple, Red, and Orange)", "purpleGeneric180", FileType.JELIB, true),

				// Uploading resources dynamically. Feature for external partner
				makeSubMenusFromResources("Loading Resource Library 'latchesL'", "anopener", FileType.JELIB, false)
				),

			// ------------------- Placement
			// mnemonic keys available: ABCDE GHIJKLMNO QRSTUVWXYZ
			new EMenu("_Placement",
				new EMenuItem("_Floorplan and Place Current Cell") {
					public void run() { Placement.floorplanCurrentCell(); }
				},
				new EMenuItem("_Place Current Cell with Preferred Algorithm") {
					public void run() { Placement.placeCurrentCell(); }
				}),

			// ------------------- Routing
			// mnemonic keys available:   D F IJK O   V XY
			new EMenu("_Routing",
				new EMenuItem.CheckBox("Enable _Auto-Stitching") {
					public boolean isSelected() { return Routing.isAutoStitchOn(); }

					public void setSelected(boolean b) {
						Routing.setAutoStitchOn(b);
						System.out.println("Auto-stitching " + (b ? "enabled" : "disabled"));
					}
				},
				new EMenuItem("Auto-_Stitch Now") {
					public void run() { AutoStitch.autoStitch(false, true); }
				},
				new EMenuItem("Auto-Stitch _Highlighted Now", KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)) {
					public void run() { AutoStitch.autoStitch(true, true); }
				},

				SEPARATOR,

				new EMenuItem.CheckBox("Enable _Mimic-Stitching") {
					public boolean isSelected() { return Routing.isMimicStitchOn(); }

					public void setSelected(boolean b) {
						Routing.setMimicStitchOn(b);
						System.out.println("Mimic-stitching " + (b ? "enabled" : "disabled"));
					}
				},
				new EMenuItem("Mimic-Stitch _Now", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0)) {
					public void run() { MimicStitch.mimicStitch(UserInterfaceMain.getEditingPreferences(), true); }
				},
				new EMenuItem("Mimic S_elected") {
					public void run() { Routing.getRoutingTool().mimicSelected(UserInterfaceMain.getEditingPreferences()); }
				},

				SEPARATOR,

				new EMenuItem("Ma_ze Route") {
					public void run() { Maze.mazeRoute(); }
				},

				SEPARATOR,

				new EMenuItem("_River-Route") {
					public void run() { River.riverRoute(); }
				},

				SEPARATOR,

				new EMenuItem("Sea-Of-_Gates Route this Cell") {
					public void run() { SeaOfGates.seaOfGatesRoute(false); }
				},
				new EMenuItem("Sea-Of-Gates Route Su_b-Cells") {
					public void run() { SeaOfGates.seaOfGatesRoute(true); }
				},
				new EMenuItem("Sea-Of-Gates Cell _Properties...") {
					public void run() { SeaOfGatesCell.showSeaOfGatesCellDialog(); }
				},

				SEPARATOR,

				new EMenuItem("Clock Routing...") {
					public void run() { ClockRouter.routeHTree(); }
				},

				SEPARATOR,

				expRoutingMenu,

				Routing.hasSunRouter() ? SEPARATOR : null,

				Routing.hasSunRouter() ? new EMenuItem("Sun _Lava Router") {
					public void run() { Routing.sunRouteCurrentCell(); }
				} : null,

				SEPARATOR,

				new EMenuItem("_Unroute Network") {
					public void run() { Routing.unrouteCurrent(); }
				},
				new EMenuItem("_Unroute Segment") {
					public void run() { Routing.unrouteCurrentSegment(); }
				},
				new EMenuItem("Get Unrouted _Wire") {
					public void run() { getUnroutedArcCommand(); }
				},
				new EMenuItem("_Copy Routing Topology") {
					public void run() { Routing.copyRoutingTopology(); }
				},
				new EMenuItem("Pas_te Routing Topology") {
					public void run() { Routing.pasteRoutingTopology(); }
				}),

			// ------------------- Generation
			// mnemonic keys available:  B DE  H JK N Q VWXYZ
			new EMenu("_Generation",
				new EMenuItem("_Coverage Implants Generator") {
					public void run() { layerCoverageCommand(LayerCoverageTool.LCMode.IMPLANT, GeometryHandler.GHMode.ALGO_SWEEP); }
				},
				new EMenuItem("_Pad Frame Generator...") {
					public void run() { padFrameGeneratorCommand(); }
				},
				new EMenuItem("_ROM Generator...") {
					public void run() { ROMGenerator.generateROM(); }
				},
				new EMenuItem("MOSIS CMOS P_LA Generator...") {
					public void run() { PLA.generate(); }
				},
				new EMenuItem("Multi-Fin_ger Transistor Cell...") {
					public void run() { MultiFingerTransistor.showMultiFingerTransistorDialog(); }
				},
				new EMenuItem("_Acute Angle Fill") {
					public void run() { acuteAngleFill(); }
				},

				SEPARATOR,

				new EMenuItem("Stitch-Based Fill Generator from doc input") {
					public void run() {
						Cell cell = WindowFrame.getCurrentCell();
						new StitchFillJob(cell, cell.getLibrary(), false);
					}
				},
				new EMenuItem("Stitch-Based Fill Generator from open windows") {
					public void run() { new StitchFillJob(null, null, false); }
				},
				new EMenuItem("_Fill (MoCMOS)...") {
					public void run() { FillGenDialog.openFillGeneratorDialog(Technology.getMocmosTechnology()); }
				},
				Technology.getTSMC180Technology() != null ? new EMenuItem("Fi_ll (TSMC180)...") {
					public void run() { FillGenDialog.openFillGeneratorDialog(Technology.getTSMC180Technology()); }
				} : null,
				Technology.getCMOS90Technology() != null ? new EMenuItem("F_ill (CMOS90)...") {
					public void run() { FillGenDialog.openFillGeneratorDialog(Technology.getCMOS90Technology()); }
				} : null,

				SEPARATOR,

				new EMenuItem("Generate gate layouts (_MoCMOS)") {
					public void run() { GateLayoutGenerator.generateFromSchematicsJob(Technology.getMocmosTechnology()); }
				},
				Technology.getTSMC180Technology() != null ? new EMenuItem("Generate gate layouts (T_SMC180)") {
					public void run() { GateLayoutGenerator.generateFromSchematicsJob(Technology.getTSMC180Technology()); }
				} : null,
				Technology.getCMOS90Technology() != null ? new EMenuItem("Generate gate layouts (CM_OS90)") {
					public void run() { GateLayoutGenerator.generateFromSchematicsJob(Technology.getCMOS90Technology()); }
				} : null,
				Job.getDebug() ? new EMenuItem("Generate gate layouts (c_urrent tech)") {
					public void run() { GateLayoutGenerator.generateFromSchematicsJob(Schematics.getDefaultSchematicTechnology()); }
				} : null),

			// ------------------- Silicon Compiler
			// mnemonic keys available: AB DEFGHIJKLM OPQRSTU WXYZ
			new EMenu("Silicon Co_mpiler",
				new EMenuItem("_Convert Current Cell to Layout") {
					public void run() { doSiliconCompilation(WindowFrame.needCurCell(), false, new SilComp.SilCompPrefs(false),
							UserInterfaceMain.getEditingPreferences()); }
				},
				new EMenuItem("_Convert Current Cell to Rats-Nest Structure") {
					public void run() { doPlaceRatsNest(WindowFrame.needCurCell(), false); }
				},

				SEPARATOR,

				new EMenuItem("Compile VHDL to _Netlist View") {
					public void run() { compileVHDL(); }
				},
				new EMenuItem("Compile _Verilog to Netlist View") {
					public void run() { compileVerilog(); }
				}),

			// ------------------- Compaction
			// mnemonic keys available: AB DEFGHIJKLMNOPQRSTUVWXYZ
			new EMenu("_Compaction",
				new EMenuItem("Do _Compaction") {
				public void run() {
					Cell cell = WindowFrame.getCurrentCell();
					if (cell == null) return;
					Compaction.compactNow(cell, Compaction.isAllowsSpreading());
				}
				}),


			MenuCommands.makeExtraMenu("pcell.gui.MainMenu", false),

			// ------------------- Information and Language Interpreters

			SEPARATOR,

			new EMenuItem("List _Tools") {
				public void run() { listToolsCommand(); }
			},

			languageMenu);
	}

	// ---------------------------- Tools Menu Commands ----------------------------

	// Logical Effort Tool
	public static void optimizeEqualGateDelaysCommand(boolean newAlg) {
		EditWindow curEdit = EditWindow.needCurrent();
		if (curEdit == null)
			return;
		LETool letool = LETool.getLETool();
		if (letool == null) {
			System.out.println("Logical Effort tool not found");
			return;
		}
		// set current cell to use global context
		curEdit.setCell(curEdit.getCell(), VarContext.globalContext, null);

		// optimize cell for equal gate delays
		if (curEdit.getCell() == null) {
			System.out.println("No current cell");
			return;
		}
		letool.optimizeEqualGateDelays(curEdit.getCell(), curEdit.getVarContext(), newAlg);
	}

	/** Print Logical Effort info for highlighted nodes */
	public static void printLEInfoCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();
		VarContext context = wnd.getVarContext();

		if (highlighter.getNumHighlights() == 0) {
			System.out.println("Nothing highlighted");
			return;
		}
		for (Highlight h : highlighter.getHighlights()) {
			if (!h.isHighlightEOBJ())
				continue;

			ElectricObject eobj = h.getElectricObject();
			if (eobj instanceof PortInst) {
				PortInst pi = (PortInst) eobj;
				pi.getInfo();
				eobj = pi.getNodeInst();
			}
			if (eobj instanceof NodeInst) {
				NodeInst ni = (NodeInst) eobj;
				LETool.printResults(ni, context);
			}
		}

	}

	public static void backAnnotateCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Cell cell = wnd.getCell();
		if (cell == null)
			return;

		BackAnnotateJob job = new BackAnnotateJob(cell);
		job.startJob();
	}

	/**
	 * Method to handle the "List Layer Coverage", "Coverage Implant Generator",
	 * polygons merge except "List Geometry on Network" commands.
	 */
	public static void layerCoverageCommand(LayerCoverageTool.LCMode func, GeometryHandler.GHMode mode) {
		Cell curCell = WindowFrame.needCurCell();
		if (curCell == null)
			return;
		LayerCoverageTool.layerCoverageCommand(func, mode, curCell, true,
				new LayerCoverageTool.LayerCoveragePreferences(false));
	}

	private static class BackAnnotateJob extends Job {
		private Cell cell;
		private LayerCoverageTool.LayerCoveragePreferences lcp = new LayerCoverageTool.LayerCoveragePreferences(
				false);

		public BackAnnotateJob(Cell cell) {
			super("BackAnnotate", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.cell = cell;
		}

        @Override
		public boolean doIt() throws JobException {
            EditingPreferences ep = getEditingPreferences();
			Cell[] schLayCells = NccUtils.findSchematicAndLayout(cell);
			if (schLayCells == null) {
				System.out.println("Could not find schematic and layout cells for " + cell.describe(true));
				return false;
			}
			if (cell.getView() == View.LAYOUT) {
				// check if layout cells match, if not, replace
				schLayCells[1] = cell;
			}

			// run NCC, get results
			NccOptions options = new NccOptions();
			NccResults results = Ncc.compare(schLayCells[0], null, schLayCells[1], null, new PassedNcc(),
					options, this);
			// get result of comparison of top schematic and layout Cells
			NccResult result = results.getResultFromRootCells();
			if (!result.match()) {
				System.out.println("Ncc failed, can't back-annotate");
				return false;
			}

			// find all wire models in schematic
			int wiresUpdated = 0;
			ArrayList<Network> networks = new ArrayList<Network>();
			HashMap<Network, NodeInst> map = new HashMap<Network, NodeInst>(); // map
																				// of
																				// networks
																				// to
																				// associated
																				// wire
																				// model
																				// nodeinst
			for (Iterator<NodeInst> it = schLayCells[0].getNodes(); it.hasNext();) {
				NodeInst ni = it.next();
				Variable var = ni.getParameterOrVariable(LENetlister.ATTR_LEWIRE);
				if (var == null)
					continue;
				var = ni.getParameterOrVariable(LENetlister.ATTR_L);
				if (var == null) {
					System.out
							.println("No attribute L on wire model " + ni.describe(true) + ", ignoring it.");
					continue;
				}
				// grab network wire model is on
				PortInst pi = ni.getPortInst(0);
				if (pi == null)
					continue;
				Netlist netlist = schLayCells[0].getNetlist(NccNetlist.SHORT_RESISTORS);
				Network schNet = netlist.getNetwork(pi);
				networks.add(schNet);
				map.put(schNet, ni);
			}
			// sort networks by name
			Collections.sort(networks, new TextUtils.NetworksByName());

			// update wire models
			for (Network schNet : networks) {
				// find equivalent network in layout
				Equivalence equiv = result.getEquivalence();
				HierarchyEnumerator.NetNameProxy proxy = equiv.findEquivalentNet(VarContext.globalContext,
						schNet);
				if (proxy == null) {
					System.out
							.println("No matching network in layout for " + schNet.getName() + ", ignoring");
					continue;
				}
				Network layNet = proxy.getNet();
				Cell netcell = layNet.getParent();
				// get wire length
				HashSet<Network> nets = new HashSet<Network>();
				nets.add(layNet);
				LayerCoverageTool.GeometryOnNetwork geoms = LayerCoverageTool.listGeometryOnNetworks(netcell,
						nets, false, GeometryHandler.GHMode.ALGO_SWEEP, lcp);
				double length = geoms.getTotalWireLength();

				// update wire length
				NodeInst ni = map.get(schNet);
				ni.updateVar(LENetlister.ATTR_L, new Double(length), ep);
				wiresUpdated++;
				System.out.println("Updated wire model " + ni.getName() + " on layout network "
						+ proxy.toString() + " to: " + length + " lambda");
			}
			System.out.println("Updated " + wiresUpdated + " wire models in " + schLayCells[0]
					+ " from layout " + schLayCells[1]);
			return true;
		}
	}

	public static void clearSizesNodableCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();

		if (highlighter.getNumHighlights() == 0) {
			System.out.println("Nothing highlighted");
			return;
		}
		for (Highlight h : highlighter.getHighlights()) {
			if (!h.isHighlightEOBJ())
				continue;

			ElectricObject eobj = h.getElectricObject();
			if (eobj instanceof PortInst) {
				PortInst pi = (PortInst) eobj;
				pi.getInfo();
				eobj = pi.getNodeInst();
			}
			if (eobj instanceof NodeInst) {
				NodeInst ni = (NodeInst) eobj;
				LETool.clearStoredSizesJob(ni);
			}
		}
		System.out.println("Sizes cleared");
	}

	public static void clearSizesCommand() {
		for (Library lib : Library.getVisibleLibraries()) {
			LETool.clearStoredSizesJob(lib);
		}
		System.out.println("Sizes cleared");
	}

	private static final double COEFFICIENTMETAL = 0.25;
	private static final double COEFFICIENTPOLY = 0.25;
	private static final double COEFFICIENTDIFF = 0.7;

	public static void estimateDelaysCommand() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;

		System.out.println("Delay estimation for each network is a ratio of the following:");
		System.out.println("  Numerator is the sum of these layers on the network:");
		System.out.println("    Transistor widths");
		System.out.println("    Diffusion half-perimeter, weighted by " + COEFFICIENTDIFF);
		System.out.println("    Polysilicon half-perimeter, weighted by " + COEFFICIENTPOLY);
		System.out.println("    Metal half-perimeter, weighted by " + COEFFICIENTMETAL);
		System.out.println("  Denominator is the width of transistors on the network");
		System.out.println("    Separate results are computed for N and P transistors, as well as their sum");
		System.out.println("-----------------------------------------------------------------------------");
		Netlist nl = cell.getNetlist();
		for (Iterator<Network> it = nl.getNetworks(); it.hasNext();) {
			Network net = it.next();
			Set<Network> nets = new HashSet<Network>();
			nets.add(net);
			LayerCoverageTool.GeometryOnNetwork geoms = LayerCoverageTool.listGeometryOnNetworks(cell, nets,
					false, GeometryHandler.GHMode.ALGO_SWEEP, new LayerCoverageTool.LayerCoveragePreferences(
							false));
			LayerCoverageTool.TransistorInfo p_gate = geoms.getPGate();
			LayerCoverageTool.TransistorInfo n_gate = geoms.getNGate();
			LayerCoverageTool.TransistorInfo p_active = geoms.getPActive();
			LayerCoverageTool.TransistorInfo n_active = geoms.getNActive();

			// compute the numerator, starting with gate widths
			double numerator = p_gate.width + n_gate.width;
			if (numerator == 0)
				continue;
			System.out.println("Network " + net.describe(true) + " ratio computation:");
			System.out.println("   N Gate width = " + n_gate.width + ", P Gate width = " + p_gate.width);

			// numerator also sums half-perimeter of each layer
			List<Layer> layers = geoms.getLayers();
			List<Double> halfPerimeters = geoms.getHalfPerimeters();
			for (int i = 0; i < layers.size(); i++) {
				Layer layer = layers.get(i);
				Layer.Function fun = layer.getFunction();
				if (!fun.isDiff() && !fun.isMetal() && !fun.isPoly())
					continue;
				if (fun.isGatePoly())
					continue;
				Double halfPerimeter = halfPerimeters.get(i);
				double coefficient = COEFFICIENTDIFF;
				if (fun.isMetal())
					coefficient = COEFFICIENTMETAL;
				if (fun.isPoly())
					coefficient = COEFFICIENTPOLY;
				double result = halfPerimeter.doubleValue() * coefficient;
				System.out.println("   Layer " + layer.getName() + " half-perimeter is "
						+ TextUtils.formatDistance(halfPerimeter.doubleValue()) + " x " + coefficient + " = "
						+ TextUtils.formatDouble(result));
				numerator += result;
			}
			System.out.println("   Numerator is the sum of these factors ("
					+ TextUtils.formatDouble(numerator) + ")");

			// show the results
			double pdenominator = p_active.width;
			double ndenominator = n_active.width;
			if (ndenominator == 0)
				System.out.println("   N denominator undefined");
			else
				System.out.println("   N denominator = " + ndenominator + ", ratio = "
						+ TextUtils.formatDouble(numerator / ndenominator));
			if (pdenominator == 0)
				System.out.println("   P denominator undefined");
			else
				System.out.println("   P denominator = " + pdenominator + ", ratio = "
						+ TextUtils.formatDouble(numerator / pdenominator));
			if (ndenominator + pdenominator == 0)
				System.out.println("   N+P Denominator undefined");
			else
				System.out.println("   N+P Denominator = " + (ndenominator + pdenominator) + ", ratio = "
						+ TextUtils.formatDouble(numerator / (ndenominator + pdenominator)));
		}
	}

	public static void addLEAttribute() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();

		List<DisplayedText> textlist = highlighter.getHighlightedText(true);
		for (DisplayedText text : textlist) {
			if (text.getElectricObject() instanceof Export) {
				new AddLEAttribute((Export) text.getElectricObject());
				return;
			}
		}
	}

	private static class AddLEAttribute extends Job {
		private Export ex;

		protected AddLEAttribute(Export ex) {
			super("Add LE Attribute", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.ex = ex;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException {
            EditingPreferences ep = getEditingPreferences();
			TextDescriptor td = ep.getAnnotationTextDescriptor()
					.withDispPart(TextDescriptor.DispPos.NAMEVALUE).withOff(-1.5, -1);
			ex.newVar(LENetlister.ATTR_le, new Double(1.0), td);
			return true;
		}
	}

	public static void loadResourceLibraries(String libraryNameWithExtension, FileType type) {
		if (Library.findLibrary(libraryNameWithExtension) != null)
			return;
		URL url = LibFile.getLibFile(libraryNameWithExtension);
		if (url == null)
			System.out.println("ERROR while loeading resource libraries: null url");
		new FileMenu.ReadLibrary(url, type, null);
	}

	/**
	 * Method to create a submenu if a library resource is available
	 */
	private static EMenuItem makeSubMenusFromResources(String submenuName, String nameWithoutExtension, FileType fileType, boolean printErrorMsg)
	{
		if (nameWithoutExtension == null)
		{
			System.out.println("ERROR while creating menus: null resource name");
			return null;
		}
		String[] exts = fileType.getExtensions();
		boolean validExtension = exts != null && exts.length > 0;
		String ext = (validExtension) ? "." + exts[0] : "";
		URL url = LibFile.getLibFile(nameWithoutExtension + ext);

		// library not found as resource. Printing message only in debug mode
		if (url == null)
		{
			if (printErrorMsg)
				System.out.println("ERROR while reading resource: " + nameWithoutExtension);
			return null;
		}
		final String resource = nameWithoutExtension + ext;
		final FileType type = fileType;

		return new EMenuItem(submenuName) {
			public void run()
        {
			loadResourceLibraries(resource, type);
        }};
	}

	/**
	 * Method to handle the "Show Network" command.
	 */
	public static void showNetworkCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Cell cell = wnd.getCell();
		if (cell == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();
		TopLevel.setBusyCursor(true);
		highlighter.showNetworks(cell);

		// 3D display if available
		WindowFrame.show3DHighlight();
		highlighter.finished();
		TopLevel.setBusyCursor(false);
	}

	/**
	 * Method to handle the "List Networks" command.
	 */
	public static void listNetworksCommand() {
		Cell cell = WindowFrame.getCurrentCell();
		if (cell == null)
			return;
		Netlist netlist = cell.getNetlist();
		if (netlist == null) {
			System.out.println("Sorry, a deadlock aborted netlist display (network information unavailable).  Please try again");
			return;
		}
		Map<Network, ArcInst[]> arcMap = null;
		if (cell.getView() != View.SCHEMATIC)
			arcMap = netlist.getArcInstsByNetwork();
		int total = 0;
		for (Iterator<Network> it = netlist.getNetworks(); it.hasNext();) {
			Network net = it.next();
			String netName = net.describe(false);
			if (netName.length() == 0)
				continue;
			StringBuffer infstr = new StringBuffer();
			infstr.append("'" + netName + "'");
			// if (net->buswidth > 1)
			// {
			// formatinfstr(infstr, _(" (bus with %d signals)"), net->buswidth);
			// }
			boolean connected = false;
			ArcInst[] arcsOnNet = null;
			if (arcMap != null)
				arcsOnNet = arcMap.get(net);
			else {
				List<ArcInst> arcList = new ArrayList<ArcInst>();
				for (Iterator<ArcInst> aIt = net.getArcs(); aIt.hasNext();)
					arcList.add(aIt.next());
				arcsOnNet = arcList.toArray(new ArcInst[] {});
			}
			for (ArcInst ai : arcsOnNet) {
				if (!connected) {
					connected = true;
					infstr.append(", on arcs:");
				}
				infstr.append(" " + ai.describe(true));
			}

			boolean exported = false;
			for (Iterator<Export> eIt = net.getExports(); eIt.hasNext();) {
				Export pp = eIt.next();
				if (!exported) {
					exported = true;
					infstr.append(", with exports:");
				}
				infstr.append(" " + pp.getName());
			}
			System.out.println(infstr.toString());
			total++;
		}
		if (total == 0)
			System.out.println("There are no networks in this cell");
	}

	/**
	 * Method to handle the "List Connections On Network" command.
	 */
	public static void listConnectionsOnNetworkCommand() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();

		Set<Network> nets = highlighter.getHighlightedNetworks();
		Netlist netlist = cell.getNetlist();
		if (netlist == null) {
			System.out
					.println("Sorry, a deadlock aborted query (network information unavailable).  Please try again");
			return;
		}
		if (nets.isEmpty()) {
			System.out.println("No network selected in list of connections on network");
			return;
		}
		for (Network net : nets) {
			System.out.println("Network " + net.describe(true) + ":");

			int total = 0;
			for (Iterator<Nodable> nIt = netlist.getNodables(); nIt.hasNext();) {
				Nodable no = nIt.next();
				NodeProto np = no.getProto();

				HashMap<Network, HashSet<Object>> portNets = new HashMap<Network, HashSet<Object>>();
				for (Iterator<PortProto> pIt = np.getPorts(); pIt.hasNext();) {
					PortProto pp = pIt.next();
					if (pp instanceof PrimitivePort && ((PrimitivePort) pp).isIsolated()) {
						NodeInst ni = (NodeInst) no;
						for (Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext();) {
							Connection con = cIt.next();
							ArcInst ai = con.getArc();
							Network oNet = netlist.getNetwork(ai, 0);
							HashSet<Object> ports = portNets.get(oNet);
							if (ports == null) {
								ports = new HashSet<Object>();
								portNets.put(oNet, ports);
							}
							ports.add(pp);
						}
					} else {
						int width = 1;
						if (pp instanceof Export) {
							Export e = (Export) pp;
							width = netlist.getBusWidth(e);
						}
						for (int i = 0; i < width; i++) {
							Network oNet = netlist.getNetwork(no, pp, i);
							HashSet<Object> ports = portNets.get(oNet);
							if (ports == null) {
								ports = new HashSet<Object>();
								portNets.put(oNet, ports);
							}
							ports.add(pp);
						}
					}
				}

				// if there is only 1 net connected, the node is unimportant
				if (portNets.size() <= 1)
					continue;
				HashSet<Object> ports = portNets.get(net);
				if (ports == null)
					continue;

				if (total == 0)
					System.out.println("  Connects to:");
				String name = null;
				if (no instanceof NodeInst)
					name = ((NodeInst) no).describe(false);
				else {
					name = no.getName();
				}
				for (Object obj : ports) {
					PortProto pp = (PortProto) obj;
					System.out.println("    Node " + name + ", port " + pp.getName());
					total++;
				}
			}
			if (total == 0)
				System.out.println("  Not connected");
		}
	}

	/**
	 * Method to handle the "List Exports On Network" command.
	 */
	public static void listExportsOnNetworkCommand() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();

		Set<Network> nets = highlighter.getHighlightedNetworks();
		Netlist netlist = cell.getNetlist();
		if (netlist == null) {
			System.out
					.println("Sorry, a deadlock aborted query (network information unavailable).  Please try again");
			return;
		}
		for (Network net : nets) {
			System.out.println("Network " + net.describe(true) + ":");

			// find all exports on network "net"
			HashSet<Export> listedExports = new HashSet<Export>();
			System.out.println("  Going up the hierarchy from " + cell + ":");
			if (findPortsUp(netlist, net, cell, listedExports))
				break;
			System.out.println("  Going down the hierarchy from " + cell + ":");
			if (findPortsDown(netlist, net, listedExports))
				break;
		}
	}

	/**
	 * Method to handle the "List Exports Below Network" command.
	 */
	public static void listExportsBelowNetworkCommand() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();

		Set<Network> nets = highlighter.getHighlightedNetworks();
		Netlist netlist = cell.getNetlist();
		if (netlist == null) {
			System.out
					.println("Sorry, a deadlock aborted query (network information unavailable).  Please try again");
			return;
		}
		for (Network net : nets) {
			System.out.println("Network " + net.describe(true) + ":");

			// find all exports on network "net"
			if (findPortsDown(netlist, net, new HashSet<Export>()))
				break;
		}
	}

	/**
	 * helper method to print all ports connected to net "net" in cell "cell",
	 * and recurse up the hierarchy.
	 *
	 * @return true if an error occurred.
	 */
	private static boolean findPortsUp(Netlist netlist, Network net, Cell cell, HashSet<Export> listedExports) {
		// look at every node in the cell
		EDatabase database = cell.getDatabase();
		for (Iterator<PortProto> it = cell.getPorts(); it.hasNext();) {
			Export pp = (Export) it.next();
			int width = netlist.getBusWidth(pp);
			for (int i = 0; i < width; i++) {
				Network ppNet = netlist.getNetwork(pp, i);
				if (ppNet != net)
					continue;
				if (listedExports.contains(pp))
					continue;
				listedExports.add(pp);
				System.out.println("    Export " + pp.getName() + " in " + cell);

				// code to find the proper instance
				Cell instanceCell = cell.iconView();
				if (instanceCell == null)
					instanceCell = cell;

				// ascend to higher cell and continue
				for (Iterator<CellUsage> uIt = instanceCell.getUsagesOf(); uIt.hasNext();) {
					CellUsage u = uIt.next();
					Cell superCell = u.getParent(database);
					Netlist superNetlist = cell.getNetlist();
					if (superNetlist == null) {
						System.out
								.println("Sorry, a deadlock aborted query (network information unavailable).  Please try again");
						return true;
					}
					for (Iterator<Nodable> nIt = superNetlist.getNodables(); nIt.hasNext();) {
						Nodable no = nIt.next();
						if (no.getProto() != cell)
							continue;
						Network superNet = superNetlist.getNetwork(no, pp, i);
						if (findPortsUp(superNetlist, superNet, superCell, listedExports))
							return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * helper method to print all ports connected to net "net" in cell "cell",
	 * and recurse down the hierarchy
	 *
	 * @return true on error.
	 */
	private static boolean findPortsDown(Netlist netlist, Network net, HashSet<Export> listedExports) {
		// look at every node in the cell
		for (Iterator<Nodable> it = netlist.getNodables(); it.hasNext();) {
			Nodable no = it.next();

			// only want complex nodes
			if (!no.isCellInstance())
				continue;
			Cell subCell = (Cell) no.getProto();

			// look at all wires connected to the node
			for (Iterator<PortProto> pIt = subCell.getPorts(); pIt.hasNext();) {
				Export pp = (Export) pIt.next();
				int width = netlist.getBusWidth(pp);
				for (int i = 0; i < width; i++) {
					Network oNet = netlist.getNetwork(no, pp, i);
					if (oNet != net)
						continue;

					// found the net here: report it
					if (listedExports.contains(pp))
						continue;
					listedExports.add(pp);
					System.out.println("    Export " + pp.getName() + " in " + subCell);
					Netlist subNetlist = subCell.getNetlist();
					if (subNetlist == null) {
						System.out
								.println("Sorry, a deadlock aborted query (network information unavailable).  Please try again");
						return true;
					}
					Network subNet = subNetlist.getNetwork(pp, i);
					if (findPortsDown(subNetlist, subNet, listedExports))
						return true;
				}
			}
		}
		return false;
	}

	/**
	 * Method to handle the "List Geometry On Network" command.
	 */
	public static void listGeometryOnNetworkCommand(GeometryHandler.GHMode mode) {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;

		HashSet<Network> nets = (HashSet<Network>) wnd.getHighlighter().getHighlightedNetworks();
		if (nets.isEmpty()) {
			System.out.println("No network in " + cell + " selected");
			return;
		}
		LayerCoverageTool.listGeometryOnNetworks(cell, nets, true, mode,
				new LayerCoverageTool.LayerCoveragePreferences(false));
	}

	private static final double SQSIZE = 0.4;

	/**
	 * Method to handle the "Show Network" command.
	 */
	public static void calculateNetworkQualityCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) return;
		Cell cell = wnd.getCell();
		if (cell == null)
		{
			System.out.println("No cell selected");
			return;
		}

		HashSet<Network> nets = (HashSet<Network>) wnd.getHighlighter().getHighlightedNetworks();
		if (nets.isEmpty()) {
			System.out.println("No network in " + cell + " selected");
			return;
		}
		WireQualityMetric metric = new WireQualityMetric("test", null);
		metric.setOutput(System.out);

		for (Iterator<Network> itNet = nets.iterator(); itNet.hasNext();)
		{
			metric.calculate(itNet.next());
		}
		metric.printAverageResults();
		TopLevel.setBusyCursor(false);
	}

	/**
	 * Method to highlight every network in the current cell using a different
	 * color
	 */
	private static void showAllNetworksCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Cell cell = wnd.getCell();
		if (cell == null)
			return;
		wnd.clearHighlighting();
		Netlist nl = cell.getNetlist();
		Map<Network, ArcInst[]> arcMap = null;
		if (cell.getView() != View.SCHEMATIC)
			arcMap = nl.getArcInstsByNetwork();
		int colors = nl.getNumNetworks();
		Color[] netColors = makeUniqueColors(colors);
		int index = 0;
		Highlighter h = wnd.getHighlighter();
		for (Iterator<Network> it = nl.getNetworks(); it.hasNext();) {
			Network net = it.next();
			ArcInst[] arcsOnNet = null;
			if (arcMap != null)
				arcsOnNet = arcMap.get(net);
			else {
				List<ArcInst> arcList = new ArrayList<ArcInst>();
				for (Iterator<ArcInst> aIt = net.getArcs(); aIt.hasNext();)
					arcList.add(aIt.next());
				arcsOnNet = arcList.toArray(new ArcInst[] {});
			}
			if (arcsOnNet.length == 0)
				continue;
			Color col = netColors[index++];
			for (ArcInst ai : arcsOnNet) {
				Poly poly = new Poly(ai.getHeadLocation(), ai.getTailLocation());
				poly.setStyle(Poly.Type.OPENED);
				h.addPoly(poly, cell, col);
				if (ai.getHeadPortInst().getNodeInst().isCellInstance()) {
					EPoint ctr = ai.getHeadLocation();
                    poly = new Poly(
                        EPoint.fromLambda(ctr.getX() - SQSIZE, ctr.getY() - SQSIZE),
                        EPoint.fromLambda(ctr.getX() - SQSIZE, ctr.getY() + SQSIZE),
                        EPoint.fromLambda(ctr.getX() + SQSIZE, ctr.getY() + SQSIZE),
                        EPoint.fromLambda(ctr.getX() + SQSIZE, ctr.getY() - SQSIZE));
					poly.setStyle(Poly.Type.CLOSED);
					h.addPoly(poly, cell, col);
				}
				if (ai.getTailPortInst().getNodeInst().isCellInstance()) {
					EPoint ctr = ai.getTailLocation();
                    poly = new Poly(
                        EPoint.fromLambda(ctr.getX() - SQSIZE, ctr.getY() - SQSIZE),
					    EPoint.fromLambda(ctr.getX() - SQSIZE, ctr.getY() + SQSIZE),
					    EPoint.fromLambda(ctr.getX() + SQSIZE, ctr.getY() + SQSIZE),
					    EPoint.fromLambda(ctr.getX() + SQSIZE, ctr.getY() - SQSIZE));
					poly.setStyle(Poly.Type.CLOSED);
					h.addPoly(poly, cell, col);
				}
			}
		}
		wnd.finishedHighlighting();
	}

	/**
	 * Method to generate unique colors. Uses this pattern R: 100 110 111 202
	 * 202 112 11 23 23 11 24 24 G: 010 101 202 111 022 121 23 11 32 24 11 42 B:
	 * 001 011 022 022 111 211 32 32 11 42 42 11 Where: 0=off 1=the main color
	 * 2=halfway between 1 and 0 3=halfway between 1 and 2 4=halfway between 2
	 * and 0
	 *
	 * @param numColors
	 *            the number of colors to generate
	 * @return an array of colors.
	 */
	private static Color[] makeUniqueColors(int numColors) {
		int numRuns = (numColors + 29) / 30;
		Color[] colors = new Color[numColors];
		int index = 0;
		for (int i = 0; i < numRuns; i++) {
			int c1 = 255 - 255 / numRuns * i;
			int c2 = c1 / 2;
			int c3 = c2 / 2;
			int c4 = (c1 + c2) / 2;

			// combinations of color 1
			if (index < numColors)
				colors[index++] = new Color(c1, 0, 0);
			if (index < numColors)
				colors[index++] = new Color(0, c1, 0);
			if (index < numColors)
				colors[index++] = new Color(0, 0, c1);

			if (index < numColors)
				colors[index++] = new Color(c1, c1, 0);
			if (index < numColors)
				colors[index++] = new Color(0, c1, c1);
			if (index < numColors)
				colors[index++] = new Color(c1, 0, c1);

			// combinations of the colors 1 and 2
			if (index < numColors)
				colors[index++] = new Color(c1, c2, 0);
			if (index < numColors)
				colors[index++] = new Color(c1, 0, c2);
			if (index < numColors)
				colors[index++] = new Color(c1, c2, c2);

			if (index < numColors)
				colors[index++] = new Color(c2, c1, 0);
			if (index < numColors)
				colors[index++] = new Color(0, c1, c2);
			if (index < numColors)
				colors[index++] = new Color(c2, c1, c2);

			if (index < numColors)
				colors[index++] = new Color(c2, 0, c1);
			if (index < numColors)
				colors[index++] = new Color(0, c2, c1);
			if (index < numColors)
				colors[index++] = new Color(c2, c2, c1);

			// combinations of colors 1, 2, and 3
			if (index < numColors)
				colors[index++] = new Color(c1, c2, c3);
			if (index < numColors)
				colors[index++] = new Color(c1, c3, c2);
			if (index < numColors)
				colors[index++] = new Color(c2, c1, c3);
			if (index < numColors)
				colors[index++] = new Color(c3, c1, c2);
			if (index < numColors)
				colors[index++] = new Color(c2, c3, c1);
			if (index < numColors)
				colors[index++] = new Color(c3, c2, c1);

			// combinations of colors 1, 2, and 4
			if (index < numColors)
				colors[index++] = new Color(c1, c2, c4);
			if (index < numColors)
				colors[index++] = new Color(c1, c4, c2);
			if (index < numColors)
				colors[index++] = new Color(c2, c1, c4);
			if (index < numColors)
				colors[index++] = new Color(c4, c1, c2);
			if (index < numColors)
				colors[index++] = new Color(c2, c4, c1);
			if (index < numColors)
				colors[index++] = new Color(c4, c2, c1);
		}
		return colors;
	}

	public static void listGeomsAllNetworksCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Cell cell = wnd.getCell();
		if (cell == null)
			return;
		new ListGeomsAllNetworksJob(cell);
	}

	/**
	 * THE RULES:
	 *
	 * - The source and drain ports of a transistor are considered driven.
	 *
	 * - A network is considered driven if it is connected to power, ground, or
	 * a driven port (with PortCharacteristic OUT or BIDIR) on an instance.
	 *
	 * - It is an error for a network in a cell to be undriven unless it is
	 * connected to an export with PortCharacteristic INPUT or INOUT
	 *
	 * Note that exporting a network as INPUT or INOUT does not make the network
	 * driven; it simply defers the possibility of an error to a higher level.
	 */
	private static void showUndrivenNetworks() {
		final ErrorLogger errorLogger = ErrorLogger.newInstance("Undriven networks");

		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;

		// Each cell visited is added to this hashset so we never visit a cell
		// twice.
		// Consider integrating this into HierarchyEnumerator; it's useful.
		final HashSet<Cell> visited = new HashSet<Cell>();

		final Object DRIVEN = new Object();
		// Equivalence classes for connected ports
		final EquivalenceClasses<Object> protoConnections = new EquivalenceClasses<Object>();

		// We perform a bottom-up traversal, but never visit a Cell
		// twice (even if it has many instances)
		HierarchyEnumerator.Visitor visitor = new HierarchyEnumerator.Visitor() {
			public CellInfo newCellInfo() {
				return new CellInfo();
			}

			public boolean enterCell(CellInfo cellInfo) {
				Cell cell = cellInfo.getCell();
				if (cell.getView() == View.ICON)
					return false;
				if (visited.contains(cell))
					return false;
				visited.add(cell);
				return true;
			}

			public void exitCell(CellInfo cellInfo) {
				Cell cell = cellInfo.getCell();
				final EquivalenceClasses<Object> instanceConnections = new EquivalenceClasses<Object>();

				for (Network net : i2i(cell.getNetlist().getNetworks())) {

					// merge this Network with all Exports it touches
					for (Export e : i2i(net.getExports())) {
						instanceConnections.merge(net, e);
						if (e.isGround() || e.isPower())
							instanceConnections.merge(e, DRIVEN);
						// Commented by DN 13-Dec-2010: Let's respect a user
						// that insists that port characteristic in not GND,
						// PWR, UNKNOWN ...
						// if (e.isNamedGround() || e.isNamedPower() ||
						// e.getCharacteristic()==PortCharacteristic.GND ||
						// e.getCharacteristic()==PortCharacteristic.PWR)
						// instanceConnections.merge(e, DRIVEN);
					}

					PortInst[] portsOnNet = null;
					Map<Network, PortInst[]> portMap = null;
					if (cell.getView() != View.SCHEMATIC)
						portMap = cell.getNetlist().getPortInstsByNetwork();
					if (portMap != null)
						portsOnNet = portMap.get(net);
					else {
						portsOnNet = net.getPortsList().toArray(new PortInst[] {});
					}

					// merge this Network with all PortInst's it touches
					for (PortInst pi : portsOnNet) {
						// merge net with portinst
						instanceConnections.merge(net, pi);

						NodeInst ni = pi.getNodeInst();
						PortProto pp = pi.getPortProto();
						if (ni.isCellInstance() && ((Cell) ni.getProto()).getView() == View.ICON)
							pp = ((Export) pp).findEquivalent(((Cell) ni.getProto()).contentsView());

						// merge instances based on prototype connectivity
						if (protoConnections.isEquivalent(pp, DRIVEN))
							instanceConnections.merge(pi, DRIVEN);
						for (PortInst pi2 : i2i(ni.getPortInsts())) {
							if (protoConnections.isEquivalent(pp, pi2.getPortProto()))
								instanceConnections.merge(pi, pi2);
						}

						if (ni.getProto() instanceof PrimitiveNode) {
							PrimitiveNode np = (PrimitiveNode) ni.getProto();
							if (np.getFunction().isResistor()) {
								for (PortInst pi2 : i2i(ni.getPortInsts()))
									instanceConnections.merge(pi, pi2);
							} else if (np.getFunction().isTransistor()) {
								if (pi == ni.getTransistorDrainPort() || pi == ni.getTransistorSourcePort())
									instanceConnections.merge(net, DRIVEN);
							}
							if (np.getFunction() == PrimitiveNode.Function.CONGROUND
									|| np.getFunction() == PrimitiveNode.Function.CONPOWER)
								instanceConnections.merge(net, DRIVEN);
						}
					}
				}

				// now check for Networks which are undriven and not connected
				// to an input Export
				OUTER: for (Network net : i2i(cell.getNetlist().getNetworks())) {
					if (instanceConnections.isEquivalent(net, DRIVEN))
						continue;
					for (Export e : i2i(net.getExports()))
						if (e.getCharacteristic() == PortCharacteristic.IN
								|| e.getCharacteristic() == PortCharacteristic.BIDIR)
							continue OUTER;
					// problem!
					List<ArcInst> arcsOnNet = null;
					Map<Network, ArcInst[]> arcMap = null;
					if (cell.getView() != View.SCHEMATIC)
						arcMap = cell.getNetlist().getArcInstsByNetwork();
					if (arcMap != null)
						arcsOnNet = new ArrayList<ArcInst>(Arrays.asList(arcMap.get(net)));
					else {
						arcsOnNet = new ArrayList<ArcInst>();
						for (Iterator<ArcInst> aIt = net.getArcs(); aIt.hasNext();)
							arcsOnNet.add(aIt.next());
					}
					if (arcsOnNet.size() > 0)
						errorLogger.logMessage("Undriven network", arcsOnNet, cell, 0, true);
					else {
						PortInst[] portsOnNet = null;
						Map<Network, PortInst[]> portMap = null;
						if (cell.getView() != View.SCHEMATIC)
							portMap = cell.getNetlist().getPortInstsByNetwork();
						if (portMap != null)
							portsOnNet = portMap.get(net);
						else {
							List<PortInst> portList = new ArrayList<PortInst>();
							for (Iterator<PortInst> pIt = net.getPorts(); pIt.hasNext();)
								portList.add(pIt.next());
							portsOnNet = portList.toArray(new PortInst[] {});
						}
						if (portsOnNet.length > 0) {
							PortInst pi = portsOnNet[0];
							errorLogger.logMessage(
									"Undriven network on node " + pi.getNodeInst().describe(false)
											+ ", port " + pi.getPortProto().getName(), null, null, cell, 0,
									true);
						}
					}
				}

				// finally, use instanceConnections to update protoConnections
				for (Export e : i2i(cell.getExports())) {
					if (instanceConnections.isEquivalent(e, DRIVEN))
						protoConnections.merge(e, DRIVEN);
					// XXX: inefficient! n^2
					for (Export e2 : i2i(cell.getExports()))
						if (instanceConnections.isEquivalent(e, e2))
							protoConnections.merge(e, e2);
				}
			}

			public boolean visitNodeInst(Nodable ni, CellInfo info) {
				NodeProto np = ni.getNodeInst().getProto();
				if (!(np instanceof Cell))
					return false;
				Cell cell = (Cell) np;
				if (visited.contains(cell))
					return false;
				return true;
			}
		};

		HierarchyEnumerator.enumerateCell(wnd.getCell(), wnd.getVarContext(), visitor);
		errorLogger.termLogging(true);
	}

	private static class ListGeomsAllNetworksJob extends Job {
		private Cell cell;
		private LayerCoverageTool.LayerCoveragePreferences lcp = new LayerCoverageTool.LayerCoveragePreferences(
				false);

		public ListGeomsAllNetworksJob(Cell cell) {
			super("ListGeomsAllNetworks", User.getUserTool(), Job.Type.CLIENT_EXAMINE, null, null,
					Job.Priority.USER);
			this.cell = cell;
			startJob();
		}

		public boolean doIt() throws JobException {
			Netlist netlist = cell.getNetlist();
			List<Network> networks = new ArrayList<Network>();
			for (Iterator<Network> it = netlist.getNetworks(); it.hasNext();) {
				networks.add(it.next());
			}

			// sort list of networks by name
			Collections.sort(networks, new TextUtils.NetworksByName());
			for (Network net : networks) {
				HashSet<Network> nets = new HashSet<Network>();
				nets.add(net);
				LayerCoverageTool.GeometryOnNetwork geoms = LayerCoverageTool.listGeometryOnNetworks(cell,
						nets, false, GeometryHandler.GHMode.ALGO_SWEEP, lcp);
				if (geoms.getTotalWireLength() == 0)
					continue;
				System.out.println("Network " + net + " has wire length " + geoms.getTotalWireLength());
			}
			return true;
		}
	}

	public static void showPowerAndGround() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();

		Netlist netlist = cell.getNetlist();
		if (netlist == null) {
			System.out
					.println("Sorry, a deadlock aborted query (network information unavailable).  Please try again");
			return;
		}
		HashSet<Network> pAndG = new HashSet<Network>();
		for (Iterator<PortProto> it = cell.getPorts(); it.hasNext();) {
			Export pp = (Export) it.next();
			if (pp.isPower() || pp.isGround()) {
				int width = netlist.getBusWidth(pp);
				for (int i = 0; i < width; i++) {
					Network net = netlist.getNetwork(pp, i);
					pAndG.add(net);
				}
			}
		}
		for (Iterator<NodeInst> it = cell.getNodes(); it.hasNext();) {
			NodeInst ni = it.next();
			PrimitiveNode.Function fun = ni.getFunction();
			if (fun != PrimitiveNode.Function.CONPOWER && fun != PrimitiveNode.Function.CONGROUND)
				continue;
			for (Iterator<Connection> cIt = ni.getConnections(); cIt.hasNext();) {
				Connection con = cIt.next();
				ArcInst ai = con.getArc();
				int width = netlist.getBusWidth(ai);
				for (int i = 0; i < width; i++) {
					Network net = netlist.getNetwork(ai, i);
					pAndG.add(net);
				}
			}
		}

		highlighter.clear();
		for (Network net : pAndG) {
			highlighter.addNetwork(net, cell);
		}
		highlighter.finished();
		if (pAndG.size() == 0)
			System.out.println("This cell has no Power or Ground networks");
	}

	private static class RepairPowerAndGround extends Job {
		protected RepairPowerAndGround() {
			super("Repair Power and Ground", User.getUserTool(), Job.Type.CHANGE, null, null,
					Job.Priority.USER);
			startJob();
		}

		public boolean doIt() throws JobException {
			validatePowerAndGround(true);
			return true;
		}
	}

	public static void validatePowerAndGround(boolean repair) {
		if (repair)
			System.out.println("Repairing power and ground exports");
		else
			System.out.println("Validating power and ground exports");
		int total = 0;
		for (Iterator<Library> lIt = Library.getLibraries(); lIt.hasNext();) {
			Library lib = lIt.next();
			for (Iterator<Cell> cIt = lib.getCells(); cIt.hasNext();) {
				Cell cell = cIt.next();
				for (Iterator<PortProto> pIt = cell.getPorts(); pIt.hasNext();) {
					Export pp = (Export) pIt.next();
					if (ImmutableExport.isNamedGround(pp.getName())
							&& pp.getCharacteristic() != PortCharacteristic.GND) {
						System.out.println("Cell " + cell.describe(true) + ", export " + pp.getName()
								+ ": does not have 'GROUND' characteristic");
						if (repair)
							pp.setCharacteristic(PortCharacteristic.GND);
						total++;
					}
					if (ImmutableExport.isNamedPower(pp.getName())
							&& pp.getCharacteristic() != PortCharacteristic.PWR) {
						System.out.println("Cell " + cell.describe(true) + ", export " + pp.getName()
								+ ": does not have 'POWER' characteristic");
						if (repair)
							pp.setCharacteristic(PortCharacteristic.PWR);
						total++;
					}
				}
			}
		}
		if (total == 0)
			System.out.println("No problems found");
		else {
			if (repair)
				System.out.println("Fixed " + total + " export problems");
			else
				System.out.println("Found " + total + " export problems");
		}
	}

	/**
	 * Method to add a "M" multiplier factor to the currently selected node.
	 */
	public static void addMultiplierCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Highlighter highlighter = wnd.getHighlighter();

		NodeInst ni = (NodeInst) highlighter.getOneElectricObject(NodeInst.class);
		if (ni == null)
			return;
		new AddMultiplier(ni);
	}

	private static class AddMultiplier extends Job {
		private NodeInst ni;

		protected AddMultiplier(NodeInst ni) {
			super("Add Spice Multiplier", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.ni = ni;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException {
            EditingPreferences ep = getEditingPreferences();
			TextDescriptor td = ep.getNodeTextDescriptor()
					.withDispPart(TextDescriptor.DispPos.NAMEVALUE).withOff(-1.5, -1);
			ni.newVar(SimulationTool.M_FACTOR_KEY, new Double(1.0), td);
			return true;
		}
	}

	/**
	 * Method to create a new template in the give NodeInst. Templates are for
	 * IO tools
	 */
	public static void makeTemplate(Variable.Key templateKey, ElectricObject eo, String value)
	{
		new MakeTemplate(templateKey, false, eo, value, EPoint.ORIGIN);
	}

	/**
	 * Method to create a new template in the current cell. Templates can be for
	 * SPICE or Verilog, depending on the Variable name.
	 *
	 * @param templateKey
	 *            the name of the variable to create.
	 * @param selectKey TODO
	 */
	public static void makeTemplate(Variable.Key templateKey, boolean selectKey) {

		Cell cell = Job.getUserInterface().needCurrentCell();
		Object val = null;

		if (selectKey) // allow to select the file from disk
		{
			String tmp = OpenFile.chooseInputFile(null, "Select File", false, User.getWorkingDirectory(), true, null);
			if (tmp != null)
				val = tmp;
		}

		new MakeTemplate(templateKey, selectKey, cell, val, (cell != null) ? cell.newVarOffset() : EPoint.ORIGIN);
	}

	private static class MakeTemplate extends Job {
		private Variable.Key templateKey;
		private ElectricObject templateObj;
		private Object value;
		private Point2D templateOffset;

		protected MakeTemplate(Variable.Key templateKey, boolean selKey, ElectricObject obj, Object val, Point2D offset) {
			super("Make template", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.templateKey = templateKey;
			this.templateObj = obj;
			if (templateObj == null)
			{
            	System.out.println("No ElectricObject available for template");
            	return;
			}

			templateOffset = offset;
			if (value != null && val != null)
				System.out.println("Overwriting original value");
			value = val;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException {
            EditingPreferences ep = getEditingPreferences();
            if (templateObj == null)
            {
            	System.out.println("Not ElectricObject available");
            	return false;
            }
			Variable templateVar = templateObj.getVar(templateKey);
			if (templateVar != null) {
				System.out.println("This ElectricObject already has the template '" + templateKey + "'");
				return false;
			}
			TextDescriptor td = ep.getCellTextDescriptor().withInterior(true)
					.withDispPart(TextDescriptor.DispPos.NAMEVALUE).withOff(templateOffset.getX(), templateOffset.getY());
			if (value == null)
			{
				templateObj.newVar(templateKey, "*Undefined", td);
			} else {
				templateObj.newVar(templateKey, value, td);
			}
			return true;
		}

        @Override
    	public void terminateOK() {
        	Variable addedVar = templateObj.getVar(templateKey);

        	if (addedVar == null)
        		System.out.println("Warning: should this happen in MakeTemplate:terminateOK?");
        	else
        	{
        		EditWindow wnd = EditWindow.getCurrent();
    			if (wnd != null && templateObj instanceof Cell)
                {
            		Highlighter highlighter = wnd.getHighlighter();
            		Cell cell = (Cell)templateObj;
            		if (addedVar.isDisplay())
            			highlighter.addText(cell, cell, addedVar.getKey());
                }
        	}
    	}
	}

	public static void getUnroutedArcCommand() {
		User.getUserTool().setCurrentArcProto(Generic.tech().unrouted_arc);
	}

	public static void padFrameGeneratorCommand() {
		String fileName = OpenFile.chooseInputFile(FileType.PADARR, null, null);
		if (fileName != null) {
			PadGenerator.makePadFrame(Library.getCurrent(), fileName, UserInterfaceMain.getEditingPreferences());
		}
	}

	private static void acuteAngleFill()
	{
		Cell curCell = WindowFrame.needCurCell();
		if (curCell == null) return;
		new AcuteAngleFill(curCell);
	}

	public static void listToolsCommand() {
		System.out.println("Tools in Electric:");
		for (Iterator<Tool> it = Tool.getTools(); it.hasNext();) {
			Tool tool = it.next();
			StringBuffer infstr = new StringBuffer();
			if (tool.isOn())
				infstr.append("On");
			else
				infstr.append("Off");
			if (tool.isBackground())
				infstr.append(", Background");
			if (tool.isFixErrors())
				infstr.append(", Correcting");
			if (tool.isIncremental())
				infstr.append(", Incremental");
			if (tool.isAnalysis())
				infstr.append(", Analysis");
			if (tool.isSynthesis())
				infstr.append(", Synthesis");
			System.out.println(tool.getName() + ": " + infstr.toString());
		}
	}

	/**
	 * Method to invoke the Bean Shell on a script file. Prompts for the file
	 * and executes it.
	 */
	public static void javaBshScriptCommand() {
		String fileName = OpenFile.chooseInputFile(FileType.JAVA, null, null);
		if (fileName != null) {
			// start a job to run the script
			EvalJavaBsh.runScript(fileName);
		}
	}

	/**
	 * Method to invoke Jython on a script file. Prompts for the file and
	 * executes it.
	 */
	private static void jythonScriptCommand() {
		if (!EvalJython.hasJython()) {
			System.out.println("Jython is not installed");
			return;
		}
		String fileName = OpenFile.chooseInputFile(FileType.JYTHON, null, null);
		if (fileName != null) {
			// start a job to run the script
			EvalJython.runScript(fileName);
		}
	}

	/**
	 * Method to change the Languages menu to include preassigned scripts.
	 */
	public static void setDynamicLanguageMenu() {
		for (EMenuBar.Instance menuBarInstance : TopLevel.getMenuBars()) {
			JMenu menu = (JMenu) menuBarInstance.findMenuItem(languageMenu.getPath());
			while (menu.getMenuComponentCount() > 4)
				menu.remove(menu.getMenuComponentCount() - 1);

			for (LanguageScripts.ScriptBinding script : LanguageScripts.getScripts()) {
				String menuName = script.fileName;
				int lastColon = menuName.lastIndexOf(':');
				int lastSlash = menuName.lastIndexOf('/');
				int lastBS = menuName.lastIndexOf('\\');
				int finalPos = Math.max(lastColon, Math.max(lastSlash, lastBS));
				menuName = menuName.substring(finalPos + 1);
				FileType type = FileType.JAVA;
				if (menuName.toLowerCase().endsWith(".bsh")) {
					menuName = menuName.substring(0, menuName.length() - 4);
				} else if (menuName.toLowerCase().endsWith(".py") || menuName.toLowerCase().endsWith(".jy")) {
					menuName = menuName.substring(0, menuName.length() - 3);
					type = FileType.JYTHON;
				}
				if (script.mnemonic != 0)
					menuName = "_" + script.mnemonic + ": " + menuName;
				EMenuItem elem = new DynamicLanguageMenuItem(menuName, script.fileName, type);
				JMenuItem item = elem.genMenu();
				menu.add(item);
			}
		}
	}

	public static class DynamicLanguageMenuItem extends EMenuItem {
		private String fileName;
		private FileType type;

		public DynamicLanguageMenuItem(String menuName, String fileName, FileType type) {
			super(menuName);
			this.fileName = fileName;
			this.type = type;
		}

		public String getDescription() {
			return "Language Scripts";
		}

		protected void updateButtons() {
		}

		public void run() {
			if (type == FileType.JAVA) {
				System.out.println("Executing commands in Bean Shell Script: " + fileName);
				EvalJavaBsh.runScript(fileName);
			} else if (type == FileType.JYTHON) {
				System.out.println("Executing commands in Python Script: " + fileName);
				EvalJython.runScript(fileName);
			}
		}
	}

	private static class DynamicExperimentalRoutingMenuItem extends EMenuItem {
		private RoutingFrame rf;

		public DynamicExperimentalRoutingMenuItem(RoutingFrame router) {
			super(router.getAlgorithmName());
			rf = router;
		}

		public String getDescription() {
			return "Experimental Routers";
		}

		protected void updateButtons() {
		}

		public void run() {
			Cell cell = Job.getUserInterface().needCurrentCell();
			if (cell == null)
				return;
			new DoExperimentalRoutingJob(rf, cell);
		}
	}

	private static class DoExperimentalRoutingJob extends Job {
		private String algorithmName;
		private Cell cell;
		private RoutingFrame.RoutingPrefs routingOptions = new RoutingFrame.RoutingPrefs(false);

		private DoExperimentalRoutingJob(RoutingFrame rf, Cell cell) {
			super("Routing", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			algorithmName = rf.getAlgorithmName();
			this.cell = cell;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException {
			for (RoutingFrame rf : RoutingFrame.getRoutingAlgorithms()) {
				if (algorithmName.equals(rf.getAlgorithmName())) {
					int numDone = rf.doRouting(cell, getEditingPreferences(), routingOptions);
					if (numDone == 0)
						return false;
					return true;
				}
			}
			return false;
		}
	}

	public static final int CONVERT_TO_VHDL = 1;
	public static final int COMPILE_VHDL_FOR_SC = 2;
	public static final int PLACE_AND_ROUTE = 4;
	public static final int SHOW_CELL = 8;
	public static final int COMPILE_VERILOG_FOR_SC = 16;
	public static final int GENERATE_CELL = 32;

	/**
	 * Method to handle the menu command to convert a cell to layout. Reads the
	 * cell library if necessary; Converts a schematic to VHDL if necessary;
	 * Compiles a VHDL or Verilog cell to a netlist if necessary; Reads the netlist from
	 * the cell; does placement and routing; Generates Electric layout; Displays
	 * the resulting layout.
	 * @param cell the cell to compile.
	 * @param doItNow if the job must executed now
	 */
	public static void doSiliconCompilation(Cell cell, boolean doItNow, SilComp.SilCompPrefs prefs, EditingPreferences ep)
	{
		if (cell == null) return;
		int activities = PLACE_AND_ROUTE | SHOW_CELL;

		// see if the current cell needs to be compiled
		if (cell.getView() != View.NETLISTQUISC)
		{
			if (cell.isSchematic())
			{
				// current cell is Schematic. See if there is a more recent
				// netlist or VHDL
				Cell vhdlCell = cell.otherView(View.VHDL);
				if (vhdlCell != null && vhdlCell.getRevisionDate().after(cell.getRevisionDate()))
					cell = vhdlCell;
				else
					activities |= CONVERT_TO_VHDL | COMPILE_VHDL_FOR_SC;
			}
			if (cell.getView() == View.VHDL)
			{
				// current cell is VHDL. See if there is a more recent netlist
				Cell netListCell = cell.otherView(View.NETLISTQUISC);
				if (netListCell != null && netListCell.getRevisionDate().after(cell.getRevisionDate()))
					cell = netListCell;
				else
					activities |= COMPILE_VHDL_FOR_SC;
			}
			if (cell.getView() == View.VERILOG)
			{
				// current cell is Verilog. See if there is a more recent netlist
				Cell netListCell = cell.otherView(View.NETLISTQUISC);
				if (netListCell != null && netListCell.getRevisionDate().after(cell.getRevisionDate()))
					cell = netListCell;
				else
					activities |= COMPILE_VERILOG_FOR_SC;
			}
		}

		if (Library.findLibrary(SilComp.SCLIBNAME) == null)
		{
			if (doItNow)
				ReadSCLibraryJob.performTaskNoJob(ep);
			else
				new ReadSCLibraryJob();
		}

		// do the silicon compilation task
		doSilCompActivityNoJob(cell, activities, prefs, doItNow);
	}

	/**
	 * Method to handle the menu command to convert a cell to a rats-nest layout. Reads the
	 * cell library if necessary; Converts a schematic to VHDL if necessary;
	 * Compiles a VHDL or Verilog cell to a netlist if necessary; Reads the netlist from
	 * the cell; creates a new cell with instances that are wired rats-nest style.
	 * @param cell the cell to compile.
	 * @param doItNow if the job must executed now
	 */
	public static void doPlaceRatsNest(Cell cell, boolean doItNow)
	{
		if (cell == null) return;
		int activities = GENERATE_CELL | SHOW_CELL;

		// see if the current cell needs to be compiled
		if (cell.getView() != View.NETLISTQUISC)
		{
			if (cell.isSchematic())
			{
				// current cell is Schematic. See if there is a more recent
				// netlist or VHDL
				Cell vhdlCell = cell.otherView(View.VHDL);
				if (vhdlCell != null && vhdlCell.getRevisionDate().after(cell.getRevisionDate()))
					cell = vhdlCell;
				else
					activities |= CONVERT_TO_VHDL | COMPILE_VHDL_FOR_SC;
			}
			if (cell.getView() == View.VHDL)
			{
				// current cell is VHDL. See if there is a more recent netlist
				Cell netListCell = cell.otherView(View.NETLISTQUISC);
				if (netListCell != null && netListCell.getRevisionDate().after(cell.getRevisionDate()))
					cell = netListCell;
				else
					activities |= COMPILE_VHDL_FOR_SC;
			}
			if (cell.getView() == View.VERILOG)
			{
				// current cell is Verilog. See if there is a more recent netlist
				Cell netListCell = cell.otherView(View.NETLISTQUISC);
				if (netListCell != null && netListCell.getRevisionDate().after(cell.getRevisionDate()))
					cell = netListCell;
				else
					activities |= COMPILE_VERILOG_FOR_SC;
			}
		}

		// do the assembly task
		new DoSilCompActivity(cell, activities, new SilComp.SilCompPrefs(false),
			new GenerateVHDL.VHDLPreferences(false), new VerilogReader.VerilogPreferences(false),
			IconParameters.makeInstance(false));
	}

	/**
	 * Method to handle the menu command to compile a VHDL cell. Compiles the
	 * VHDL in the current cell and creates a netlist cell.
	 */
	public static void compileVHDL() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		if (cell.getView() != View.VHDL) {
			System.out.println("Must be editing a VHDL cell before compiling it");
			return;
		}

		// do the VHDL compilation task
		new DoSilCompActivity(cell, COMPILE_VHDL_FOR_SC | SHOW_CELL, new SilComp.SilCompPrefs(false),
			new GenerateVHDL.VHDLPreferences(false), new VerilogReader.VerilogPreferences(false),
			IconParameters.makeInstance(false));
	}

	/**
	 * Method to handle the menu command to compile a Verilog cell. Compiles the
	 * Verilog in the current cell and creates a netlist cell.
	 */
	public static void compileVerilog() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		if (cell.getView() != View.VERILOG) {
			System.out.println("Must be editing a VERILOG cell before compiling it");
			return;
		}

		// do the Verilog compilation task
		new DoSilCompActivity(cell, COMPILE_VERILOG_FOR_SC | SHOW_CELL,
			new SilComp.SilCompPrefs(false), new GenerateVHDL.VHDLPreferences(false),
			new VerilogReader.VerilogPreferences(false), IconParameters.makeInstance(false));
	}

	/**
	 * Method to handle the menu command to make a VHDL cell. Converts the
	 * schematic in the current cell to VHDL and displays it.
	 */
	public static void makeVHDL() {
		Cell cell = WindowFrame.needCurCell();
		if (cell == null)
			return;
		new DoSilCompActivity(cell, CONVERT_TO_VHDL | SHOW_CELL, new SilComp.SilCompPrefs(false),
				new GenerateVHDL.VHDLPreferences(false), new VerilogReader.VerilogPreferences(false),
				IconParameters.makeInstance(false));
	}

	/**
	 * Class to read the Silicon Compiler support library in a new job.
	 */
	private static class ReadSCLibraryJob extends Job {
		private ReadSCLibraryJob() {
			super("Read Silicon Compiler Library", User.getUserTool(), Job.Type.CHANGE, null, null,
					Job.Priority.USER);
			startJob();
		}

		public static boolean performTaskNoJob(EditingPreferences ep) {
			// read standard cell library
			System.out.println("Reading Standard Cell Library '" + SilComp.SCLIBNAME + "'");
			URL fileURL = LibFile.getLibFile(SilComp.SCLIBNAME + ".jelib");
			LibraryFiles.readLibrary(ep, fileURL, null, FileType.JELIB, true);
			return true;
		}

        @Override
		public boolean doIt() throws JobException {
			return performTaskNoJob(getEditingPreferences());
		}
	}

	public static boolean doSilCompActivityNoJob(Cell cell, int activities, SilComp.SilCompPrefs prefs, boolean doItNow) {
        EditingPreferences ep = new EditingPreferences(doItNow, cell.getTechPool());
		GenerateVHDL.VHDLPreferences vhp = new GenerateVHDL.VHDLPreferences(doItNow);
		VerilogReader.VerilogPreferences vep = new VerilogReader.VerilogPreferences(doItNow);
		IconParameters ip = IconParameters.makeInstance(doItNow);
		if (doItNow) {
			List<Cell> textCellsToRedraw = new ArrayList<Cell>();
			try {
				DoSilCompActivity.performTaskNoJob(cell, textCellsToRedraw, activities, ep, prefs, vhp, vep, ip, false);
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		} else
			new DoSilCompActivity(cell, activities, prefs, vhp, vep, ip);
		return true;
	}

	/**
	 * Class to do the next silicon-compilation activity in a Job.
	 */
	private static class DoSilCompActivity extends Job {
		private Cell cell;
		private int activities;
		private List<Cell> textCellsToRedraw;
		private SilComp.SilCompPrefs prefs;
		private GenerateVHDL.VHDLPreferences vhp;
		private VerilogReader.VerilogPreferences vep;
		private IconParameters ip;
		private boolean isIncludeDateAndVersionInOutput = User.isIncludeDateAndVersionInOutput();

		private DoSilCompActivity(Cell cell, int activities, SilComp.SilCompPrefs prefs,
				GenerateVHDL.VHDLPreferences vhp, VerilogReader.VerilogPreferences vep, IconParameters ip) {
			super("Silicon-Compiler activity", User.getUserTool(), Job.Type.CHANGE, null, null,
					Job.Priority.USER);
			this.cell = cell;
			this.activities = activities;
			this.prefs = prefs;
			this.vhp = vhp;
			this.vep = vep;
			this.ip = ip;
			startJob();
		}

        @Override
		public boolean doIt() throws JobException {
			fieldVariableChanged("cell");
			textCellsToRedraw = new ArrayList<Cell>();
			fieldVariableChanged("textCellsToRedraw");
			cell = performTaskNoJob(cell, textCellsToRedraw, activities, getEditingPreferences(), prefs, vhp, vep,
				ip, isIncludeDateAndVersionInOutput);
			if (cell == null) {
				activities = 0;
				fieldVariableChanged("activities");
			}
			return true;
		}

		@Override
		public void terminateOK() {
			for (Cell cell : textCellsToRedraw) {
				TextWindow.updateText(cell);
			}
			if ((activities & SHOW_CELL) != 0) {
				WindowFrame wf = WindowFrame.getCurrentWindowFrame();
				if (User.isShowCellsInNewWindow())
					wf = null;
				if (wf == null)
					wf = WindowFrame.createEditWindow(cell);
				wf.setCellWindow(cell, null);
			}
		}

		public static Cell performTaskNoJob(Cell cell, List<Cell> textCellsToRedraw, int activities,
				EditingPreferences ep, SilComp.SilCompPrefs prefs, GenerateVHDL.VHDLPreferences vhp, VerilogReader.VerilogPreferences vep,
				IconParameters ip, boolean isIncludeVersionAndDateInOutput) throws JobException {
			Library destLib = cell.getLibrary();
			textCellsToRedraw = new ArrayList<Cell>();

			if ((activities & CONVERT_TO_VHDL) != 0) {
				// convert Schematic to VHDL
				System.out.print("Generating VHDL from " + cell + " ...");
				List<String> vhdlStrings = GenerateVHDL.convertCell(cell, vhp);
				if (vhdlStrings == null) {
					System.out.println("No VHDL produced");
					return null;
				}

				String cellName = cell.getName() + "{vhdl}";
				Cell vhdlCell = cell.getLibrary().findNodeProto(cellName);
				if (vhdlCell == null) {
					vhdlCell = Cell.makeInstance(ep, cell.getLibrary(), cellName);
					if (vhdlCell == null)
						return null;
				}
				String[] array = new String[vhdlStrings.size()];
				for (int i = 0; i < vhdlStrings.size(); i++)
					array[i] = vhdlStrings.get(i);
				vhdlCell.setTextViewContents(array, ep);
				textCellsToRedraw.add(vhdlCell);
				System.out.println(" Done, created " + vhdlCell);
				cell = vhdlCell;
			}

			if ((activities & COMPILE_VHDL_FOR_SC) != 0) {
				// compile the VHDL to a netlist
				System.out.print("Compiling VHDL in " + cell + " ...");
				CompileVHDL c = new CompileVHDL(cell);
				if (c.hasErrors()) {
					System.out.println("ERRORS during compilation, no netlist produced");
					return null;
				}
				List<String> netlistStrings = c.getQUISCNetlist(destLib, isIncludeVersionAndDateInOutput);
				if (netlistStrings == null) {
					System.out.println("No netlist produced");
					return null;
				}

				// store the QUISC netlist
				String cellName = cell.getName() + "{net.quisc}";
				Cell netlistCell = cell.getLibrary().findNodeProto(cellName);
				if (netlistCell == null) {
					netlistCell = Cell.makeInstance(ep, cell.getLibrary(), cellName);
					if (netlistCell == null)
						return null;
				}
				String[] array = new String[netlistStrings.size()];
				for (int i = 0; i < netlistStrings.size(); i++)
					array[i] = netlistStrings.get(i);
				netlistCell.setTextViewContents(array, ep);
				textCellsToRedraw.add(netlistCell);
				System.out.println(" Done, created " + netlistCell);
				cell = netlistCell;
			}

			if ((activities & COMPILE_VERILOG_FOR_SC) != 0) {
				// compile the VHDL to a netlist
				System.out.println("Compiling Verilog in " + cell + " ...");
				CompileVerilogStruct c = new CompileVerilogStruct(cell, true);
				if (c.hasErrors()) {
					System.out.println("ERRORS during compilation, no netlist produced");
					return null;
				}

				if ((activities & GENERATE_CELL) != 0) {
					// just make the cell (randomly placed)
					cell = c.genCell(destLib, !vep.makeLayoutCells, ep, ip);
					if (cell != null)
						System.out.println("Done, created " + cell.describe(false));
				} else {
					// save the netlist in a view
					List<String> netlistStrings = c.getQUISCNetlist(destLib, isIncludeVersionAndDateInOutput);
					if (netlistStrings == null) {
						System.out.println("No netlist produced");
						return null;
					}

					// store the QUISC netlist
					String cellName = cell.getName() + "{net.quisc}";
					Cell netlistCell = cell.getLibrary().findNodeProto(cellName);
					if (netlistCell == null) {
						netlistCell = Cell.makeInstance(ep, cell.getLibrary(), cellName);
						if (netlistCell == null)
							return null;
					}
					String[] array = new String[netlistStrings.size()];
					for (int i = 0; i < netlistStrings.size(); i++)
						array[i] = netlistStrings.get(i);
					netlistCell.setTextViewContents(array, ep);
					textCellsToRedraw.add(netlistCell);
					System.out.println("Done, created " + netlistCell);
					cell = netlistCell;
				}
			}

			if ((activities & PLACE_AND_ROUTE) != 0) {
				// first grab the information in the netlist
				System.out.println("Reading netlist in " + cell);
				GetNetlist gnl = new GetNetlist();
				if (gnl.readNetCurCell(cell)) {
					System.out.println("Error compiling netlist");
					return null;
				}

				// do the placement
				System.out.println("Placing cells");
				Place place = new Place(prefs);
				String err = place.placeCells(gnl);
				if (err != null) {
					System.out.println(err);
					return null;
				}

				// do the routing
				System.out.println("Routing cells");
				Route route = new Route(prefs);
				err = route.routeCells(gnl);
				if (err != null) {
					System.out.println(err);
					return null;
				}

				// generate the results
				System.out.println("Generating layout");
				Maker maker = new Maker(ep, prefs);
				Object result = maker.makeLayout(destLib, gnl);
				if (result instanceof String) {
					System.out.println((String) result);
					if (Technology.getCurrent() == Schematics.tech()) {
						System.out
								.println("Should switch to a layout technology first (currently in Schematics)");
						return null;
					}
				}
				if (!(result instanceof Cell))
					return null;
				cell = (Cell) result;
				System.out.println("Created " + cell);
			}
			return cell;
		}
	}

	/****************** Parasitic Tool ********************/

	public static void parasiticCommand() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Cell cell = wnd.getCell();
		Highlighter highlighter = wnd.getHighlighter();

		Set<Network> nets = highlighter.getHighlightedNetworks();
		for (Network net : nets) {
			ParasiticTool.getParasiticTool().netwokParasitic(net, cell);
		}
	}

	public static void importAssuraDrcErrors() {
		String fileName = OpenFile.chooseInputFile(FileType.ERR, null, null);
		if (fileName == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Cell cell = wnd.getCell();
		if (cell == null)
			return;
		HashMap<Cell, String> mangledNames = new HashMap<Cell, String>();
		com.sun.electric.tool.io.output.GDS.buildUniqueNames(cell, mangledNames,
				IOTool.getGDSCellNameLenMax(), IOTool.isGDSOutUpperCase());
		AssuraDrcErrors.importErrors(fileName, mangledNames, "DRC");
	}

	/**
	 * Method to import Calibre DRC Errors into Electric and display them as
	 * ErrorLogger.
	 */
	public static void importCalibreDrcErrors() {
		String fileName = OpenFile.chooseInputFile(FileType.DB, null, null);
		if (fileName == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) {
			System.out.println("No current cell to import data in '" + fileName + "' to");
			return;
		}
		Cell cell = wnd.getCell();
		if (cell == null) {
			System.out.println("No current cell to import data in '" + fileName + "' to");
			return;
		}
		HashMap<Cell, String> mangledNames = new HashMap<Cell, String>();
		com.sun.electric.tool.io.output.GDS.buildUniqueNames(cell, mangledNames,
				IOTool.getGDSCellNameLenMax(), IOTool.isGDSOutUpperCase());
		CalibreDrcErrors.importErrors(fileName, mangledNames, "DRC", false);
	}

	/**
	 * Method to import Calibre LVS Errors into Electric and display them as
	 * ErrorLogger.
	 */
	public static void importCalibreLVSErrors() {
		String fileName = OpenFile.chooseInputFile(FileType.DB, null, null);
		if (fileName == null)
			return;
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null) {
			System.out.println("No current cell to import data in '" + fileName + "' to");
			return;
		}
		Cell cell = wnd.getCell();
		if (cell == null) {
			System.out.println("No current cell to import data in '" + fileName + "' to");
			return;
		}
		HashMap<Cell, String> mangledNames = new HashMap<Cell, String>();
		com.sun.electric.tool.io.output.GDS.buildUniqueNames(cell, mangledNames,
				IOTool.getGDSCellNameLenMax(), IOTool.isGDSOutUpperCase());
		CalibreLVSErrors.importErrors(fileName, cell, "LVS", false);
	}

	/**
	 * Method to export DRC deck to XML file
	 */
	private static void exportDRCDeck() {
		String fileName = OpenFile.chooseOutputFile(FileType.XML, "Save XML DRC deck for foundry '"
				+ Technology.getCurrent().getSelectedFoundry() + "'", null);
		if (fileName == null)
			return;

		System.out.println("Exporting DRC Deck from technology '" + Technology.getCurrent().getTechName() + "' into '" + fileName + "'");
		DRCTemplate.exportDRCDecks(fileName, Technology.getCurrent());
	}

	public static void importDRCDeck() {
		String fileName = OpenFile.chooseInputFile(FileType.XML, "Open XML DRC deck", false);
		if (fileName == null)
			return;
		Technology tech = Technology.getCurrent();
		DRCTemplate.DRCXMLParser parser = DRCTemplate.importDRCDeck(TextUtils.makeURLToFile(fileName),
				tech.getXmlTech(), true);
		String message = "Deck file '" + fileName + "' loaded ";

		message += (parser.isParseOK()) ? "without errors." : " with errors. No rules loaded.";

		JOptionPane.showMessageDialog(TopLevel.getCurrentJFrame(), message, "Import DRC Deck",
				(parser.isParseOK()) ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);

		if (!parser.isParseOK())
			return; // errors in the file

		new ImportDRCDeckJob(parser.getRules(), tech);
	}

	private static class ImportDRCDeckJob extends Job {
		private List<DRCTemplate.DRCXMLBucket> rules;
		private Technology tech;
		private DRC.DRCPreferences dp = new DRC.DRCPreferences(false);

		public ImportDRCDeckJob(List<DRCTemplate.DRCXMLBucket> rules, Technology tech) {
			super("ImportDRCDeck", User.getUserTool(), Job.Type.CHANGE, null, null, Job.Priority.USER);
			this.rules = rules;
			this.tech = tech;
			startJob();
		}

		public boolean doIt() {
			System.out.println("Importing DRC Deck into technology '" + tech.getTechName() + "'");
			for (DRCTemplate.DRCXMLBucket bucket : rules) {
				boolean done = false;

				// set the new rules under the foundry imported
				for (Iterator<Foundry> itF = tech.getFoundries(); itF.hasNext();) {
					Foundry f = itF.next();
					if (f.getType().getName().equalsIgnoreCase(bucket.foundry)) {
						f.setRules(bucket.drcRules);
						System.out.println("New DRC rules for foundry '" + f.getType().getName()
								+ "' were loaded in '" + tech.getTechName() + "'");
						// Need to clean cells using this foundry because the
						// rules might have changed.
						DRC.cleanCellsDueToFoundryChanges(tech, f, dp);
						// Only when the rules belong to the selected foundry,
						// then reload the rules
						if (f == tech.getSelectedFoundry())
							tech.setCachedRules(null);
						// tech.setState();
						done = true;
						break;
					}
				}
				if (!done) {
					Job.getUserInterface()
							.showErrorMessage(
									"'" + bucket.foundry + "' is not a valid foundry in '"
											+ tech.getTechName() + "'", "Importing DRC Deck");
				}
			}
			return true;
		}
	}

	public static void runNccSchematicCrossProbing() {
		EditWindow wnd = EditWindow.needCurrent();
		if (wnd == null)
			return;
		Cell cell = wnd.getCell();
		if (cell == null)
			return;
		NccCrossProbing.runNccSchematicCrossProbing(cell, wnd.getVarContext());
	}

	// private static void newAutoFill(boolean hierarchy, boolean binary)
	// {
	// Cell cell = WindowFrame.getCurrentCell();
	// if (cell == null) return;
	//
	// FillGeneratorTool.generateAutoFill(cell, hierarchy, binary, false);
	// }

	private static void plotChosen() {
		String fileName = OpenFile.chooseInputFile(null, null, null);
		if (fileName == null)
			return;
		URL fileURL = TextUtils.makeURLToFile(fileName);
		String cellName = TextUtils.getFileNameWithoutExtension(fileURL);
		Library curLib = Library.getCurrent();
		Cell cell = curLib.findNodeProto(cellName);
		if (cell == null) {
			CellBrowser dialog = new CellBrowser(TopLevel.getCurrentJFrame(), true,
					CellBrowser.DoAction.selectCellToAssoc);
			dialog.setVisible(true);
			cell = dialog.getSelectedCell();
			if (cell == null)
				return;
		}
		SimulationData.plot(cell, fileURL, null);
	}
}
