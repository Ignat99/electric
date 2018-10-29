	/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: WaveformTest.java
 *
 * Copyright (c) 2010, Static Free Software. All rights reserved.
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
package com.sun.electric.tool.user.tests;

import com.sun.electric.database.EditingPreferences;
import com.sun.electric.database.hierarchy.Cell;
import com.sun.electric.database.hierarchy.Library;
import com.sun.electric.database.hierarchy.View;
import com.sun.electric.technology.TechPool;
import com.sun.electric.tool.Job;
import com.sun.electric.tool.io.FileType;
import com.sun.electric.tool.io.input.LibraryFiles;
import com.sun.electric.tool.io.input.SimulationData;
import com.sun.electric.tool.io.output.PNG;
import com.sun.electric.tool.simulation.Engine;
import com.sun.electric.tool.simulation.Sample;
import com.sun.electric.tool.simulation.ScalarSample;
import com.sun.electric.tool.simulation.Signal;
import com.sun.electric.tool.simulation.SignalCollection;
import com.sun.electric.tool.simulation.SimulationTool;
import com.sun.electric.tool.simulation.Stimuli;
import com.sun.electric.tool.simulation.SweptSample;
import com.sun.electric.tool.simulation.als.ALS;
import com.sun.electric.tool.simulation.irsim.IRSIM;
import com.sun.electric.tool.user.MessagesStream;
import com.sun.electric.tool.user.User;
import com.sun.electric.tool.user.ui.WindowFrame;
import com.sun.electric.tool.user.waveform.Panel;
import com.sun.electric.tool.user.waveform.WaveSignal;
import com.sun.electric.tool.user.waveform.WaveformWindow;
import com.sun.electric.util.ClientOS;
import com.sun.electric.util.TextUtils;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Class to test the waveform window.
 *
 * Notes on the tests:
 *   H01: Transient
 *   H02: Transient (19 sweeps), Measurements
 *   H03: Transient (30 sweeps), DC (sweeps), Measurements
 *   H04: Transient (7 sweeps), DC, AC (sweeps), Measurements
 *   H05: Transient
 *   H06: Transient
 *   H07: Transient (25 sweeps), Measurements
 *   H08: Transient (3 sweeps), AC, Measurements, early EOF
 *   H09: Transient (3 sweeps), AC (sweeps)
 *   H10: Transient (4 sweeps), AC (sweeps)
 *   H11: Transient, AC
 *   H12:
 *   H13: ? (sweeps), multi-file
 *   LT:  DC (6 sweeps)
 */
public class WaveformTest extends AbstractGUITest
{
	private final String testName;
	private final String cellName;
	private final String stimuliExtension;
	private final String cmdFileExtension;
	private final int engine;
	private final PanelSpec[] panelSpecs;
	private final boolean locked;

	private transient Panel[] panels;

	public WaveformTest(String commandName, String testName, String cellName,
		String stimuliExtension, boolean locked, int engine, String cmdFileExtension,
		PanelSpec spec1)
	{
		this(commandName, testName, cellName, stimuliExtension, locked, engine, cmdFileExtension,
			new PanelSpec[] {spec1});
	}

	public WaveformTest(String commandName, String testName, String cellName,
		String stimuliExtension, boolean locked, int engine, String cmdFileExtension,
		PanelSpec spec1, PanelSpec spec2)
	{
		this(commandName, testName, cellName, stimuliExtension, locked, engine, cmdFileExtension,
			new PanelSpec[] {spec1, spec2});
	}

	public WaveformTest(String commandName, String testName, String cellName,
		String stimuliExtension, boolean locked, int engine, String cmdFileExtension,
		PanelSpec spec1, PanelSpec spec2, PanelSpec spec3)
	{
		this(commandName, testName, cellName, stimuliExtension, locked, engine, cmdFileExtension,
			new PanelSpec[] {spec1, spec2, spec3});
	}

	public WaveformTest(String commandName, String testName, String cellName,
		String stimuliExtension, boolean locked, int engine, String cmdFileExtension,
		PanelSpec spec1, PanelSpec spec2, PanelSpec spec3, PanelSpec spec4)
	{
		this(commandName, testName, cellName, stimuliExtension, locked, engine, cmdFileExtension,
			new PanelSpec[] {spec1, spec2, spec3, spec4});
	}

	public WaveformTest(String commandName, String testName, String cellName,
		String stimuliExtension, boolean locked, int engine, String cmdFileExtension,
		PanelSpec... panelSpecs)
	{
		super(commandName);
		this.testName = testName;
		this.cellName = cellName;
		this.stimuliExtension = stimuliExtension;
		this.locked = locked;
		this.engine = engine;
		this.cmdFileExtension = cmdFileExtension;
		this.panelSpecs = panelSpecs;
	}

	public static List<AbstractTest> getTests()
	{
		List<AbstractTest> list = new ArrayList<AbstractTest>();
		list.add(new WaveformTest("ALS1", "ALS-1", "ALS-1{sch}", null, true, SimulationTool.ALS_ENGINE, ".vec",
			new PanelSpec(null, "a"),
			new PanelSpec(null, "b"),
			new PanelSpec(null, "c")));
		if (IRSIM.hasIRSIM())
		{
			list.add(new WaveformTest("IRSIM1", "IRSIM-1", "IRSIM-1{lay}", null, true, SimulationTool.IRSIM_ENGINE, ".cmd"));
			list.add(new WaveformTest("IRSIM2", "IRSIM-2", "IRSIM-2{lay}", null, true, SimulationTool.IRSIM_ENGINE, ".cmd"));
			list.add(new WaveformTest("IRSIM3", "IRSIM-3", "IRSIM-3{lay}", ".sim", true, SimulationTool.IRSIM_ENGINE, ".cmd"));
		}
		list.add(new WaveformTest("Spice2", "Spice2-1", "Spice2-1{sch}", ".spo", true, -1, null,
			new PanelSpec(null, "signal 1", "signal 2")));
		list.add(new WaveformTest("HSpice1", "SpiceH-1", "SpiceH-1{sch}", ".tr0", true, -1, null,
			new PanelSpec(null, "5:net_16_"),
			new PanelSpec(null, "1:net_198_", "3:net_198_")));
		list.add(new WaveformTest("HSpice2", "SpiceH-2", "SpiceH-2{sch}", ".tr0", true, -1, null,
			new PanelSpec("MEASUREMENTS", "n10d0", "n10d150")));
		list.add(new WaveformTest("HSpice3", "SpiceH-3", "SpiceH-3{sch}", ".tr0", false, -1, null,
			new PanelSpec("DC SIGNALS", "a5.rp1.rrp_sub@1.v(1,39:3_"),
			new PanelSpec("DC SIGNALS", "rshortc"),
			new PanelSpec("TRANS SIGNALS", "v(c1,ref")));
		list.add(new WaveformTest("HSpice4", "SpiceH-4", "SpiceH-4{sch}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "aa", "bb"),
			new PanelSpec("DC SIGNALS", "cat"),
			new PanelSpec("MEASUREMENTS", "temper")));
		list.add(new WaveformTest("HSpice5", "SpiceH-5", "SpiceH-5{sch}", ".tr0", true, -1, null,
			new PanelSpec(null, "net@4", "net@3"),
			new PanelSpec(null, "i(vpulse@0", "i(vvdd")));
		list.add(new WaveformTest("HSpice6", "SpiceH-6", "SpiceH-6{sch}", ".tr0", true, -1, null,
			new PanelSpec(null, "txclk", "clk_path_test.invclk[3]_out_"),
			new PanelSpec(null, "i(vvdd", "i(vclk")));
		list.add(new WaveformTest("HSpice7", "SpiceH-7", "SpiceH-7{lay}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "in0", "out"),
			new PanelSpec("MEASUREMENTS", "inbufstr", "index", "outloadstr")));
		list.add(new WaveformTest("HSpice8", "SpiceH-8", "SpiceH-8{sch}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "v(inn"),
			new PanelSpec("AC SIGNALS", "gain1"),
			new PanelSpec("MEASUREMENTS", "index", "alter")));
		list.add(new WaveformTest("HSpice9", "SpiceH-9", "SpiceH-9{sch}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "1"),
			new PanelSpec("AC SIGNALS", "1", "i(v2")));
		list.add(new WaveformTest("HSpice10", "SpiceH-10", "SpiceH-10{sch}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "1"),
			new PanelSpec("AC SIGNALS", "1", "i(v2")));
		list.add(new WaveformTest("HSpice11", "SpiceH-11", "SpiceH-11{sch}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "out"),
			new PanelSpec("AC SIGNALS", "net@5", "out")));
		list.add(new WaveformTest("HSpice12", "SpiceH-12", "SpiceH-12{sch}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "a_b_", "a_bn_", "an_b_", "an_bn_")));
		list.add(new WaveformTest("HSpice13", "SpiceH-13", "SpiceH-13{sch}", ".tr0", false, -1, null,
			new PanelSpec("TRANS SIGNALS", "1", "2")));
		list.add(new WaveformTest("EpicSpice1", "SpiceEpic-1", "SpiceEpic-1{sch}", ".out", true, -1, null,
			new PanelSpec(null, "in", "out")));
		list.add(new WaveformTest("EpicSpice2", "SpiceEpic-2", "SpiceEpic-2{sch}", ".out", true, -1, null,
			new PanelSpec(null, "1", "2")));
		list.add(new WaveformTest("LTSpice1", "SpiceLT-1", "SpiceLT-1{sch}", ".raw", true, -1, null,
			new PanelSpec(null, "net@1", "net@23"),
			new PanelSpec(null, "i(vvsd)")));
		list.add(new WaveformTest("PSpice1", "SpiceP-1", "SpiceP-1{lay}", ".txt", true, -1, null,
			new PanelSpec(null, "i(c1)", "ib(q3)", "ic(q3)"),
			new PanelSpec(null, "w(rc1)", "w(rc2)")));
		list.add(new WaveformTest("SmartSpice1", "SpiceSmart-1", "SpiceSmart-1{lay}", ".raw", true, -1, null,
			new PanelSpec(null, "in", "out"),
			new PanelSpec(null, "m:node3:node13#internal#source")));
		list.add(new WaveformTest("SpiceOpus1", "SpiceOpus-1", "SpiceOpus-1{sch}", ".raw", true, -1, null,
			new PanelSpec(null, "vin", "vout")));
		list.add(new WaveformTest("SpiceOpus2", "SpiceOpus-2", "SpiceOpus-2{sch}", ".raw", true, -1, null,
			new PanelSpec(null, "lind0#branch", "vout")));
		list.add(new WaveformTest("SpiceOpus3", "SpiceOpus-3", "SpiceOpus-3{sch}", ".raw", true, -1, null,
			new PanelSpec(null, "mm1#body", "mm1#dbody"),
			new PanelSpec(null, "mm1#gate", "sweep"),
			new PanelSpec(null, "vgnd#branch", "vds#branch")));
		list.add(new WaveformTest("Verilog-1", "Verilog-1", "Verilog-1{sch}", ".dump", true, -1, null,
			new PanelSpec(null, "test_bench.extest_"),
			new PanelSpec(null, "test_bench.tdi_"),
			new PanelSpec(null, "test_bench.instruction[0:7]_"),
			new PanelSpec(null, "test_bench.j.tms_")));
		return list;
	}

	public static String getOutputDirectory()
	{
		String rootPath = User.getRegressionPath();
		if (rootPath == null) return null;
		return rootPath + "/tools/Waveform/output/";
	}

	public static class PanelSpec implements Serializable
	{
		private String[] signalNames;
		private String collectionName;

		public PanelSpec(String colName, String sig1)
		{
			collectionName = colName;
			signalNames = new String[] {sig1};
		}

		public PanelSpec(String colName, String sig1, String sig2)
		{
			collectionName = colName;
			signalNames = new String[] {sig1, sig2};
		}

		public PanelSpec(String colName, String sig1, String sig2, String sig3)
		{
			collectionName = colName;
			signalNames = new String[] {sig1, sig2, sig3};
		}

		public PanelSpec(String colName, String sig1, String sig2, String sig3, String sig4)
		{
			collectionName = colName;
			signalNames = new String[] {sig1, sig2, sig3, sig4};
		}
	}

	/**
	 * This is run in a change Job.
	 */
	@Override
	protected Collection<URL> getRequiredLibraries(String regressionPath)
	{
		if (Library.findLibrary(testName) != null) return super.getRequiredLibraries(regressionPath);

		String trueRootPath = regressionPath;
		String libPath = trueRootPath + "data/libs/";
		String libFileName = libPath + testName + ".jelib";
		URL libFileURL = TextUtils.makeURLToFile(libFileName);
		return Collections.singleton(libFileURL);
	}

	/**
	 * Phase 1 is run in the actual Job after libraries are read.
	 */
	protected boolean phase1()
	{
		if (engine == SimulationTool.ALS_ENGINE)
		{
			Library lib = Library.findLibrary(testName);
			if (lib == null)
			{
				System.out.println("Error reading library '" + testName + "'");
				return false;
			}
			Cell cell = lib.findNodeProto(cellName);
			SimulationTool.startSimulation(engine, null, cell, null, true);
		}
		return true;
	}

	/**
	 * Phase 2 is run in the terminateOK phase of the Job.
	 */
	protected boolean phase2()
	{
		String trueRootPath = workingDir();
		String libPath = trueRootPath + "data/libs/";

		User.setWaveformDigitalPanelHeight(100);
		User.setWaveformAnalogPanelHeight(100);

		Library lib = Library.findLibrary(testName);
		if (lib == null)
		{
			System.out.println("Error reading library '" + testName + "'");
			return false;
		}
		Cell cell = lib.findNodeProto(cellName);
		if (engine == SimulationTool.ALS_ENGINE)
		{
			Cell nlCell = cell.otherView(View.NETLISTALS);
			ALS.simulateNetlist(nlCell, cell, new Stimuli());
		} else
		{
			// close all previous waveform windows
			List<WindowFrame> openWWs = new ArrayList<WindowFrame>();
			for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext();)
			{
				WindowFrame wf = it.next();
				if (wf.getContent() instanceof WaveformWindow) openWWs.add(wf);
			}
			for (WindowFrame wf : openWWs)
				wf.finished();

			if (engine < 0)
			{
				// read the simulation output
				String waveFile = libPath + testName + stimuliExtension;
				URL url = TextUtils.makeURLToFile(waveFile);
				String netDelimeter = SimulationTool.getSpiceExtractedNetDelimiter();
				Stimuli stimuli = SimulationData.processInput(cell, url, netDelimeter);
				WaveformWindow.showSimulationDataInNewWindow(stimuli);
			} else
			{
				Job.getUserInterface().displayCell(cell);
				String stimFile = null;
				if (stimuliExtension != null) stimFile = libPath + testName + stimuliExtension;
				SimulationTool.startSimulation(engine, stimFile, null, null, true);
			}
		}

		// look for a waveform window
		WindowFrame waveFrame = null;
		for (Iterator<WindowFrame> it = WindowFrame.getWindows(); it.hasNext();)
		{
			WindowFrame wf = it.next();
			if (wf.getContent() instanceof WaveformWindow)
			{
				waveFrame = wf;
				break;
			}
		}
		if (waveFrame == null)
		{
			System.out.println("ERROR: No waveform window was created");
			return false;
		}

		// force waveform window to be a fixed size
		WaveformWindow ww = (WaveformWindow)waveFrame.getContent();
		Rectangle waveLoc = new Rectangle(20, 20, 1000, 500);
		waveFrame.setWindowSize(waveLoc);

		// remove all existing panels
		ww.clearAllPanels();
		if (!locked) ww.togglePanelXAxisLock();

		// make the requested panels
		panels = new Panel[panelSpecs.length];
		for (int p = 0; p < panelSpecs.length; p++)
		{
			// find the SignalCollection that will be used in this panel
			String collectionName = panelSpecs[p].collectionName;
			SignalCollection sc = null;
			for (Iterator<SignalCollection> it = ww.getSimData().getSignalCollections(); it.hasNext();)
			{
				SignalCollection oneSC = it.next();
				if (collectionName == null || collectionName.equals(oneSC.getName()))
				{
					sc = oneSC;
					break;
				}
			}
			if (sc == null)
			{
				System.out.println("Could not find SignalCollection in waveform data");
				return false;
			}

			// make the waveform window panel
			Panel wp = ww.makeNewPanel(100);
			panels[p] = wp;

			// add signals to the panel
			String[] signals = panelSpecs[p].signalNames;
			for (int i = 0; i < signals.length; i++)
			{
				Signal<?> sig = sc.findSignal(signals[i]);
				if (sig == null)
				{
					System.out.println("Could not find Signal '" + signals[i] + "' in waveform data");
					return false;
				}
				if (sig != null) new WaveSignal(wp, sig);
			}

			// make the panel fill the screen
			ww.fillScreen();
		}

		// run command file if requested
		if (cmdFileExtension != null)
		{
			Engine e = ww.getSimData().getEngine();
			String stimFile = libPath + testName + cmdFileExtension;
			try
			{
				e.restoreStimuli(TextUtils.makeURLToFile(stimFile));
			} catch (IOException ex)
			{
				ex.printStackTrace();
				return false;
			}

			// make the panels fill the screen
			ww.fillScreen();
		}
		return true;
	}

	/**
	 * Phase 3 is run in a new Swing thread.
	 */
	protected boolean phase3()
	{
		String trueRootPath = workingDir();
		String outputPath = trueRootPath + "output/";
		String expectedPath = trueRootPath + "data/expected/";
		String osPrefix = ClientOS.getOSPrefix();
		if (osPrefix.equals("Win"))
		{
			double version = ClientOS.getOSVersion();
			if (version == 7) osPrefix += "7"; else
				if (version > 7) osPrefix += "8";
		}

		boolean passed = true;
		try
		{
			for (int p = 0; p < panels.length; p++)
			{
				Panel wp = panels[p];

				// dump the waveform panel data to disk and check it
				char suffix = (char)('a' + p);
				String testPanelName = testName + suffix;
				String csvFileName = outputPath + testPanelName + ".csv";
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(csvFileName)));
				wp.dumpDataCSV(pw);
				pw.close();
				String expectedCSVFileName = expectedPath + testPanelName + ".csv";
				boolean same = compareResults(csvFileName, expectedCSVFileName);
				if (!same)
				{
					passed = false;
					continue;
				}

				// now dump the waveform panel image to disk
				String imageFileName = outputPath + testPanelName + ".png";
				Image img = wp.getWaveImage();
				int wid = img.getWidth(null);
				int hei = img.getHeight(null);
				int leftEdge = Math.max(0, wp.getVertAxisPos() - 10);
				BufferedImage bi = new BufferedImage(wid - leftEdge, hei, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = bi.createGraphics();
				g.drawImage(img, 0, 0, wid - leftEdge, hei, leftEdge, 0, wid, hei, null);
				PNG.writeImage(bi, imageFileName);

				// check the images
				String expectedImageFileName = expectedPath + testPanelName + osPrefix + ".png";
				same = compareImages(p + 1, imageFileName, expectedImageFileName);
				if (!same)
				{
					passed = false;
					continue;
				}
			}
		} catch (Exception e)
		{
			System.out.println("Exception: " + e);
			e.printStackTrace();
			passed = false;
		}
		return passed;
	}

	public boolean runWaveformTestBatch()
	{
		String trueRootPath = workingDir();
		String libPath = trueRootPath + "data/libs/";
		String outputPath = trueRootPath + "output/";
		String expectedPath = trueRootPath + "data/expected/";

		boolean passed = true;
		try
		{
			// setup test
			ensureOutputDirectory(outputPath);
			MessagesStream.getMessagesStream().save(outputPath + testName + ".log");

			// read the test library
			String libFileName = libPath + testName + ".jelib";
			URL libFileURL = TextUtils.makeURLToFile(libFileName);
			Library lib = Library.findLibrary(testName);
			EditingPreferences ep = new EditingPreferences(true, TechPool.getThreadTechPool());
			if (lib == null) lib = LibraryFiles.readLibrary(ep, libFileURL, null, FileType.JELIB, false);
			if (lib == null)
			{
				System.out.println("WaveformTest.runWaveformTest can't open '" + libFileURL.getFile() + "'");
				return false;
			}

			// read the simulation output
			Cell cell = lib.findNodeProto(cellName);
			String waveFile = libPath + testName + stimuliExtension;
			URL url = TextUtils.makeURLToFile(waveFile);
			String netDelimeter = SimulationTool.getFactorySpiceExtractedNetDelimiter();
			Stimuli stimuli = SimulationData.processInput(cell, url, netDelimeter);

			// make the requested panels
			for (int p = 0; p < panelSpecs.length; p++)
			{
				// find the SignalCollection that will be used in this panel
				String collectionName = panelSpecs[p].collectionName;
				SignalCollection sc = null;
				for (Iterator<SignalCollection> it = stimuli.getSignalCollections(); it.hasNext();)
				{
					SignalCollection oneSC = it.next();
					if (collectionName == null || collectionName.equals(oneSC.getName()))
					{
						sc = oneSC;
						break;
					}
				}
				if (sc == null)
				{
					System.out.println("Could not find SignalCollection in waveform data");
					return false;
				}

				// add signals to the panel
				String[] signals = panelSpecs[p].signalNames;

				// dump the waveform panel data to disk and check it
				char suffix = (char)('a' + p);
				String testPanelName = testName + suffix;
				String csvFileName = outputPath + testPanelName + ".csv";
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(csvFileName)));
				for (int k = 0; k < signals.length; k++)
				{
					Signal<?> as = sc.findSignal(signals[k]);
					if (as == null)
					{
						System.out.println("Could not find Signal '" + signals[k] + "' in waveform data");
						pw.close();
						return false;
					}
					Signal.View<?> waveform = ((Signal<?>)as).getExactView();
					int numEvents = waveform.getNumEvents();
					for (int i = 0; i < numEvents; i++)
					{
						Sample samp = waveform.getSample(i);
						double time = waveform.getTime(i);
						if (samp instanceof SweptSample<?>)
						{
							SweptSample<?> sws = (SweptSample<?>)samp;
							for (int s = 0; s < sws.getWidth(); s++)
							{
								Sample ss = sws.getSweep(s);
								pw.println("\"" + time + "\",\"" + s + "\",\"" + ((ScalarSample)ss).getValue() + "\"");
							}
						} else
						{
							ScalarSample ss = (ScalarSample)samp;
							pw.println("\"" + time + "\",\"" + ss.getValue() + "\"");
						}
					}
					pw.println();
				}
				pw.close();
				String expectedCSVFileName = expectedPath + testPanelName + ".csv";
				boolean same = compareResults(csvFileName, expectedCSVFileName);
				if (!same)
				{
					passed = false;
				}
			}
		} catch (Exception e)
		{
			System.out.println("Exception: " + e);
			e.printStackTrace();
			passed = false;
		}
		return passed;
	}
}
